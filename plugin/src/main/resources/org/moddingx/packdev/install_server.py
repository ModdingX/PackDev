#!/usr/bin/env python3

# PackDev server install script
# https://github.com/ModdingX/PackDev

import json
import os
import subprocess
from typing import List
from urllib.request import Request, urlopen


def setup_server():
    mods = []
    with open('server.txt') as file:
        for entry in file.read().split('\n'):
            if not entry.strip() == '' and '/' in entry:
                mods.append([entry[:entry.index('/')], entry[entry.index('/') + 1:]])

    try:
        os.remove('run.sh')
        os.remove('run.bat')
    except FileNotFoundError:
        pass

    loader: str = mods[0][0]
    insv: str = mods[0][1]
    mcv: str = mods[1][0]
    mlv: str = mods[1][1]
    
    if loader == 'forge':
        install_forge(mcv, mlv)
    elif loader == 'fabric':
        install_fabric(insv, mcv, mlv)
    elif loader == 'quilt':
        install_quilt(insv, mcv, mlv)
    else:
        raise EnvironmentError(f'Loader {loader} is not supported')

    print('Downloading Mods')
    if not os.path.isdir('mods'):
        os.makedirs('mods')
    for mod in mods[2:]:
        download_mod(mod[1], mod[0])


def install_forge(mcv: str, mlv: str):
    print('Installing Forge')
    run_installer(
        f'https://maven.minecraftforge.net/net/minecraftforge/forge/{mcv}-{mlv}/forge-{mcv}-{mlv}-installer.jar',
        ['--installServer']
    )

    if os.path.exists('run.sh'):
        # New installer format 1.17 onwards
        pass
    else:
        # Old installer format before 1.17
        try:
            print('Processing installer output')
            if os.path.exists(f'{mcv}.json'):
                with open(f'{mcv}.json') as file:
                    minecraft_json = json.loads(file.read())
                os.remove(f'{mcv}.json')
                with open(f'{mcv}.json', mode='w') as file:
                    file.write(json.dumps(minecraft_json, indent=4))
        except FileNotFoundError:
            print('Failed to process forge installer output.')

        create_install_scripts(f'forge-{mcv}-{mlv}.jar', mcv, force_old_java=True)

    print('Adding version specific files')

    def apply_log4j_fix(file_url: str, file_name: str, no_lookup: bool = False):
        download_file(file_url, file_name)
        with open('user_jvm_args.txt', mode='a') as file:
            file.write(f'\n-Dlog4j.configurationFile={file_name}\n')
            if no_lookup:
                file.write('-Dlog4j2.formatMsgNoLookups=true\n')

    if mcv == '1.16.4' or mcv == '1.16.5' or is_major_mc(mcv, '1.17') or is_major_mc(mcv, '1.18'):
        apply_log4j_fix('https://files.minecraftforge.net/log4shell/1.16.4/log4j2_server.xml', 'log4j2_server.xml', no_lookup=not is_major_mc(mcv, '1.16'))
    elif is_major_mc(mcv, '1.13') or is_major_mc(mcv, '1.14') or is_major_mc(mcv, '1.15') or is_major_mc(mcv, '1.16'):
        apply_log4j_fix('https://files.minecraftforge.net/log4shell/1.13/log4j2_server.xml', 'log4j2_server.xml')
    elif is_major_mc(mcv, '1.12'):
        apply_log4j_fix('https://files.minecraftforge.net/log4shell/1.12/log4j2_server.xml', 'log4j2_server.xml')
    elif is_major_mc(mcv, '1.7') or is_major_mc(mcv, '1.8') or is_major_mc(mcv, '1.9') or is_major_mc(mcv, '1.10') or is_major_mc(mcv, '1.11'):
        apply_log4j_fix('https://files.minecraftforge.net/log4shell/1.7/log4j2_server.xml', 'log4j2_server.xml')


def install_fabric(insv: str, mcv: str, mlv: str):
    print('Installing Fabric')
    run_installer(
        f'https://maven.fabricmc.net/net/fabricmc/fabric-installer/{insv}/fabric-installer-{insv}.jar',
        ['server', '-dir', '.', '-mcversion', mcv, '-loader', mlv]
    )
    create_install_scripts('fabric-server-launch.jar', mcv)


def install_quilt(insv: str, mcv: str, mlv: str):
    print('Installing Quilt')
    run_installer(
        f'https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/{insv}/quilt-installer-{insv}.jar',
        ['install', 'server', mcv, mlv, '--install-dir=.', '--download-server']
    )
    create_install_scripts('quilt-server-launch.jar', mcv)


def run_installer(url: str, args: List[str]):
    download_file(url, 'installer.jar')
    subprocess.check_call(['java', '-jar', 'installer.jar', *args])

    try:
        os.remove('installer.jar')
        os.remove('installer.jar.log')
    except FileNotFoundError:
        pass


def create_install_scripts(main_jar: str, mcv: str, force_old_java: bool = False):
    if not os.path.exists('user_jvm_args.txt'):
        with open('user_jvm_args.txt', mode='w') as file:
            file.write('# Add custom JVM arguments here\n')
    if not os.path.exists('run.sh'):
        with open('run.sh', mode='w') as file:
            file.write('#!/usr/bin/env sh\n')
            if force_old_java or is_old_java(mcv):
                # Can't use @user_jvm_args.txt here as java 8 doesn't understand it.
                file.write(f'java $(sed -E \'s/^([^#]*)(#.*)?$/\\1/\' user_jvm_args.txt) -jar {main_jar} "$@"\n')
            else:
                file.write(f'java @user_jvm_args.txt -jar {main_jar} "$@"\n')
    if not os.path.exists('run.bat'):
        with open('run.bat', mode='w') as file:
            file.write(f'java @user_jvm_args.txt -jar {main_jar} %*\n')
            file.write('pause\n')


def is_old_java(mcv: str) -> bool:
    for ver in range(1, 17): # Until 1.16 minecraft uses java 8
        if is_major_mc(mcv, f'1.{ver}'):
            return True
    return False


def is_major_mc(mcv: str, expected: str) -> bool:
    return mcv == expected or mcv.startswith(expected + '.')


def download_mod(file_url: str, file_name: str):
    # CurseForge sometimes fails, so we need retries.
    print(f'Downloading mod {file_name}')
    attempts = 0
    while True:
        try:
            request = make_request(file_url)
            response = urlopen(request)
            with open('mods' + os.path.sep + file_name, mode='wb') as target:
                target.write(response.read())
            break
        except Exception as e:
            attempts += 1
            if attempts > 10:
                raise e
            print('Retry download')


def download_file(file_url: str, file_name: str):
    response = urlopen(make_request(file_url))
    with open(file_name, mode='wb') as file:
        file.write(response.read())


def make_request(file_url) -> Request:
    return Request(file_url, headers={'Accept': '*/*', 'User-Agent': 'python3/packdev server installer'})


if __name__ == '__main__':
    setup_server()

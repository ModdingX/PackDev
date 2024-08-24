FROM eclipse-temurin:${jdk}
LABEL org.opencontainers.image.title="${name#}"
LABEL org.opencontainers.image.version="${version#}"
LABEL org.opencontainers.image.description="${name#} version ${version#} for minecraft ${minecraft#}"
RUN apt-get update && apt-get install -y tini python3 git && apt-get clean
EXPOSE 25565
COPY . /data
WORKDIR /data
RUN python3 install.py && echo "eula=true" > /data/eula.txt && chmod +x /data/run.sh
ENTRYPOINT [ "tini", "--" ]
CMD [ "sh", "run.sh", "--nogui" ]

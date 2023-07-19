FROM eclipse-temurin:${jdk}
RUN apt-get update
RUN apt-get install -y python3 git sudo
EXPOSE 25565 25575
COPY . /data
WORKDIR /data
RUN python3 install.py
RUN echo "eula=true" > /data/eula.txt
RUN chmod +x /data/run.sh
WORKDIR /data
ENTRYPOINT [ "sh", "run.sh", "--nogui" ]

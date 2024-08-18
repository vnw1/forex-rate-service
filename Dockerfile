FROM openjdk:11

RUN apt-get update && \
    apt-get install -y curl && \
    curl -L -o sbt-1.8.0.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-1.8.0.deb && \
    dpkg -i sbt-1.8.0.deb && \
    rm sbt-1.8.0.deb && \
    apt-get update && \
    apt-get install -y sbt

WORKDIR /app

COPY . .

EXPOSE 8090

CMD ["sbt", "run"]
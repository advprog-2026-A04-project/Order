FROM ubuntu:latest
LABEL authors="anita"

ENTRYPOINT ["top", "-b"]
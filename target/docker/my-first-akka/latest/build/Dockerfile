FROM alpine:3.14
COPY maven /maven/
ENTRYPOINT echo "Listing dependencies copied:"; cp --verbose /maven/*.jar /opt/local-lib/

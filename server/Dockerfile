FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /navi-server .

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /navi-server /navi-server
ENV DATA_DIR=/data PORT=8080
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["/navi-server"]

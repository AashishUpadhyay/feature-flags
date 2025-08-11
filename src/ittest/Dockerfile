FROM golang:1.20
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY *.go ./
RUN go install gotest.tools/gotestsum@v1.10.0
COPY entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh
RUN mkdir -p data
ENTRYPOINT [ "./entrypoint.sh" ]


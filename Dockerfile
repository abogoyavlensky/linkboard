FROM --platform=linux/amd64 clojure:temurin-21-tools-deps-1.12.0.1479-alpine AS build

# Install bb
RUN apk --no-cache add curl \
  && curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install \
  && chmod +x install \
  && ./install --version 1.12.194 --static

# Install tailwindcss
RUN curl -sL -o /usr/local/bin/tailwindcss https://github.com/tailwindlabs/tailwindcss/releases/download/v3.4.15/tailwindcss-linux-x64 \
  && chmod +x /usr/local/bin/tailwindcss

# Build uberjar
WORKDIR /app
COPY . /app
RUN bb build


FROM --platform=linux/amd64 eclipse-temurin:21.0.2_13-jre-alpine
LABEL org.opencontainers.image.source=https://github.com/abogoyavlensky/linkboard

WORKDIR /app
COPY --from=build /app/target/standalone.jar /app/standalone.jar

EXPOSE 80
CMD ["java", "-Xmx256m", "-jar", "standalone.jar"]

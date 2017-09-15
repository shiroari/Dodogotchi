FROM artifactory.netent.com:5000/netent/noss-java-base:latest_int_test

RUN mkdir -p /app /app/bin /app/etc /app/logs /app/cache /app/data

COPY build/libs/app-shadow.jar /app/bin/app.jar
COPY config.yaml /app/etc/config.yaml

RUN chgrp -R 0 /app \
  && chmod -R g+rwX /app

EXPOSE 9090

CMD ["java", "-Xms64m", "-Xmx64m", "-jar", "/app/bin/app.jar", \
  "-Dconfig=/app/etc/config.yaml", \
  "-Dvertx.cacheDirBase=/app/cache"]

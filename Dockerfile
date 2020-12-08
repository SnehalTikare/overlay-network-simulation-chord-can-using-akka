FROM java:8-jdk-alpine

COPY out/artifacts/group2_overlaynetworksimulator_jar/group2-overlaynetworksimulator.jar /usr/app/

WORKDIR /usr/app

ENTRYPOINT ["java", "-jar", "group2-overlaynetworksimulator.jar"]


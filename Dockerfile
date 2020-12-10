FROM java:8-jdk-alpine

COPY target/scala-2.12/group2-overlaynetworksimulator-assembly-0.1.jar /usr/app/

WORKDIR /usr/app

ENTRYPOINT ["java", "-jar", "group2-overlaynetworksimulator-assembly-0.1.jar"]


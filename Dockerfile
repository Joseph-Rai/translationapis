# Base Image
FROM openjdk:11-jre-slim

# Create App Directory
RUN mkdir /app

# Set Working Directory
WORKDIR /app

# Copy Jar File
COPY target/translationapis-0.0.1-SNAPSHOT.jar /app/translation-api.jar

# Expose Port
EXPOSE 8080

# Run Application
CMD ["java", "-jar", "translation-api.jar"]
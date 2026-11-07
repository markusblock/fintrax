# Fintrax

Fintrax is a desktop personal-finance application for 
- syncing banking data with a bank account
- import and export banking data
- organizing transactions with automatic categorization and labeling 

## Requirements

- Java Development Kit (JDK) 21
- A desktop environment to run the JavaFX application

Gradle is included through the wrapper; no separate Gradle installation is needed.

## Get started

Clone the repository and move into the project directory:

```sh
git clone https://github.com/markusblock/fintrax.git
cd fintrax
```

Start the application on macOS or Linux:

```sh
./gradlew :app:run
```

On Windows, run `gradlew.bat :app:run` instead.

## Build and test

Build the project and run its tests:

```sh
./gradlew build
```

On Linux without a graphical display, run the build with Xvfb:

```sh
xvfb-run ./gradlew build
```

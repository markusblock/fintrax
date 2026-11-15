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

## Database storage

By default, Fintrax stores its database in `~/.fintrax/data` (`~` is your user home directory; for example, `~/` on macOS/Linux and `%USERPROFILE%` on Windows).

To use a different directory, pass `fintrax.storage.path` as an application argument:

```sh
./gradlew :app:run --args="--fintrax.storage.path=$HOME/.fintrax/data-empty"
```

On Windows:

```bat
gradlew.bat :app:run --args="--fintrax.storage.path=%USERPROFILE%\.fintrax\data-empty"
```

Fintrax creates the directory if it does not exist. Using a different path keeps the default database unchanged and starts with a separate, empty database.

## Build and test

Build the project and run its tests:

```sh
./gradlew build
```

On Linux without a graphical display, run the build with Xvfb:

```sh
xvfb-run ./gradlew build
```

## Continuous integration

GitHub Actions runs build and tests, then Sonar analysis, as separate steps for pushes to `main` and pull requests from this repository. The separate step timings make build/test time distinguishable from analysis time. Pull requests from forks run the build and tests only because they cannot access the Sonar token.

Sonar analysis generates the aggregated JaCoCo coverage report and may run its prerequisite test tasks when their outputs are missing or stale. It runs under Xvfb for that reason.

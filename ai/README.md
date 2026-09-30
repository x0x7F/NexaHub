# Environment Setup

## Prerequisites

- Install CMake
- Install the NDK and set the `ANDROID_NDK` environment variable

## git submodule

Run the following command in the project root to initialize submodules:

```bash
git submodule update --init --recursive
```

Note: run this command from the root of the git repository (`rikkahub/`), not from the `src/main/cpp/mnn` directory.

## Building libMNN.so

Go to the `src/main/cpp/mnn` directory and run:

```bash
./build.sh
```

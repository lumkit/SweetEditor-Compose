# Core Library

SweetEditor's native editor core is based on the open-source `OpenSweetEditor` project.

## Reference

- [FinalScave/OpenSweetEditor](https://github.com/FinalScave/OpenSweetEditor)

## In This Repository

- `editor-core` contains the native kernel and C API integration surface
- `editor-compose` wraps the native core for Kotlin Multiplatform and Compose

## Notes

- The Compose layer should consume native capabilities instead of re-implementing core editing algorithms
- Native kernel changes and Kotlin wrapper changes should be evaluated separately

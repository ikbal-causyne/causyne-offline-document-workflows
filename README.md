# Offline Document Workflows on Android: A Source-Level Study of PDF and Office Writers

[![Paper DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.23600894.svg)](https://doi.org/10.5281/zenodo.23600894)

**Read the paper:** [10-page PDF](paper/paper2_review.pdf) · [LaTeX source](paper/main.tex)

This public preprint is published on Zenodo ([DOI: 10.5281/zenodo.23600894](https://doi.org/10.5281/zenodo.23600894)), and has not been peer reviewed. Cite the paper for its analysis and this repository for code reuse. No journal publication is claimed.

A source-level study of Causyne's Android PDF workflows. Selected, unchanged Android implementation files are under `android-source/`; `ANDROID_SOURCE_MANIFEST.json` records their original paths and hashes. They are a source snapshot, not a standalone Android app.

The separate Kotlin/JVM prototype under `src/` generates basic DOCX, XLSX, and PPTX files. Run its tests with JDK 17 and `./gradlew test`. A small fixture test opens all three formats with Apache POI. When supplied two slides, its PPTX writer emits only one; the test records this limitation. Android behavior, visual fidelity, other readers, and device resource use remain unverified.

The manuscript is in [`paper/`](paper/) and is a working preprint, not a peer-reviewed publication. Its author is Ikbal Fadilah ([ORCID](https://orcid.org/0009-0008-4853-135X)).

The original code is licensed under [MIT](LICENSE). The manuscript and its PDF are licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Reuse of the manuscript requires attribution. Please cite the paper when using its findings; the repository's [`CITATION.cff`](CITATION.cff) describes the research code.

---
version: "0.1.2"
level: auto
processes:
  design: copilot
  implementation: copilot
  testing: auto
  documentation: auto
  review: none
  deployment: auto
---

This format is based on [AI-DECLARATION.md](https://ai-declaration.md/en/0.1.2).

## Notes

- Navi was built with Claude (Anthropic), working as a coding agent in Claude Code.
- **The human side:** I look at everything. I decide how things should look and work: the idea, every feature request, the logo, the colours and the Material 3 Expressive direction, the languages, the setup flow, sharing and the website. I review everything, make changes myself, test on real devices, decide what ships, and maintain the project.
- **The AI side:** most of the code (the Android app, the Go server, the website), written on request and reviewed by the human, the tests, the docs, the build workflow, release builds and the server deployment. Screens were also checked on an Android emulator.
- **Design** is `copilot`: the AI proposed layouts and the visual system, and the human directed, corrected and approved it.
- **Implementation** is `copilot`: the AI wrote most of the code on request, and the human reviewed every change and made some changes directly.
- **Review** is `none`: the human reviewed the code without AI help.
- **Contributors:** if you use AI for a contribution, please say so in the pull request.

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

Navi was built with Claude (Anthropic) as a coding agent in Claude Code.

What I do: I look at everything. I decide how it should look and what it should do, from the idea and the features to the logo, the colours and the wording. I review everything, change things myself, test on real phones and decide what ships.

What the AI does: most of the code (app, server, website), the tests, the docs, the build workflow and the deployment, all on request and reviewed by me. It also checked screens in an Android emulator.

Design is `copilot` because the AI suggested layouts and I steered and approved them. Implementation is `copilot` because the AI wrote most of the code and I reviewed and edited it. Review is `none` because I review the code myself, without AI.

If you use AI for a contribution, please say so in the pull request.

## Reporting Bugs & Feature Requests

For general discussion and troubleshooting not special to Android, please use the [Support
Forum](https://forum.syncthing.net/). For actionable Android related bug reports and feature
requests, please file issues in the [GitHub Issue
Tracker](https://github.com/researchxxl/syncthing-android/issues), using one of the
provided templates.

:warning: Do not submit AI generated issues or comments. Report any details
you experienced as a human, and let anyone who wants to perform AI analysis
do so on their own.

## Contributing Translations

All translations are done via
[Weblate](https://hosted.weblate.org/projects/syncthing-fork/). If you wish
to contribute to a translation, just head over there and sign up.
Before every release, the language resources are updated from the
latest info on Weblate.

## Contributing Documentation

Updates to the [documentation site](https://github.com/researchxxl/syncthing-android/tree/main/wiki) can be
made as pull requests.

## Contributing Code

We welcome contributions. However:

:warning: We do not accept contributions that are wholly or mostly AI
generated. You may assume that we are fully capable of prompting an AI
ourselves when so inclined.

:warning: We do not accept unsolicited pull requests from new contributors.
Most such contributions fall afoul of the previous rule. If yours does not,
please post a message to the relevant issue with a link to your branch and a
short description of the reasoning behind it. We'll take care of onboarding
you.

### The Developer Certificate of Origin (DCO)

The Syncthing-Fork project requires the Developer Certificate of Origin (DCO)
sign-off on pull requests (PRs). This means that all commit messages must
contain a signature line to indicate that the developer accepts the DCO.

The DCO is a lightweight way for contributors to certify that they wrote (or
otherwise have the right to submit) the code and changes they are
contributing to the project. Here is the full [text of the
DCO](https://developercertificate.org):

---

By making a contribution to this project, I certify that:

1. The contribution was created in whole or in part by me and I have the
   right to submit it under the open source license indicated in the file;
   or

2. The contribution is based upon previous work that, to the best of my
   knowledge, is covered under an appropriate open source license and I have
   the right under that license to submit that work with modifications,
   whether created in whole or in part by me, under the same open source
   license (unless I am permitted to submit under a different license), as
   indicated in the file; or

3. The contribution was provided directly to me by some other person who
   certified (1), (2) or (3) and I have not modified it.

4. I understand and agree that this project and the contribution are public
   and that a record of the contribution (including all personal information
   I submit with it, including my sign-off) is maintained indefinitely and
   may be redistributed consistent with this project or the open source
   license(s) involved.

---

Contributors indicate that they adhere to these requirements by adding
a `Signed-off-by` line to their commit messages.  For example:

    This is my commit message

    Signed-off-by: Random J Developer <random@developer.example.org>

The name and email address in this line must match those of the committing
author.

### Coding Style

#### General

- All text files use Unix line endings. The git settings already present in
  the repository attempt to enforce this.

- When making changes, follow the brace and parenthesis style of the
  surrounding code.

### Commits

- Commit messages (and pull request titles) should follow the [conventional
  commits](https://www.conventionalcommits.org/en/v1.0.0/) specification and
  be in lower case.

- We use a scope description in the commit message subject. This is the
  component of Syncthing-Fork that the commit affects. For example, `deps`,
  `onboarding`, `RestApi`, `SyncthingService`, etc. If the commit doesn't
  affect a specific component, such as for changes to the build system or
  documentation, the scope should be omitted. The same goes for changes that
  affect many components which would be cumbersome to list.

- Commits that resolve an existing issue must include the issue number
  as `(fixes #123)` at the end of the commit message subject. A correctly
  formatted commit message subject looks like this:

      feat(RestApi): add local completion result cache (fixes #1000)

- If the commit message subject doesn't say it all, one or more paragraphs of
  describing text should be added to the commit message. This should explain
  why the change is made and what it accomplishes.

- When drafting a pull request, please feel free to add commits with
  corrections and merge from `main` when necessary. This provides a clear time
  line with changes and simplifies review. Do not, in general, rebase your
  commits, as this makes review harder.

- Pull requests are merged to `main` using squash merge. The "stream of
  consciousness" set of commits described in the previous point will be reduced
  to a single commit at merge time. The pull request title and description will
  be used as the commit message.

### Tests

We do not have a strategy for adding tests yet. If you write tests, please keep
them at your fork repository and avoid introducing new dependencies only required
to run the tests.

## Licensing

All contributions are made available under the same license as the already
existing material being contributed to. For most of the project and unless
otherwise stated this means MPLv2, but there are exceptions:

- Certain libraries may have a separate license, indicated by
  the presence of a LICENSE file in the corresponding directory.

Regardless of the license in effect, you retain the copyright to your
contribution.

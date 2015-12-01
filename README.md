# jira-triage

### Setup

```bash
mkdir ~/.jira.d

cat <<EOM >~/.jira.d/config.yml
endpoint: https://jira.__YOUR_COMPANY__.com
project: __YOUR_PROJECT__
username: __USERNAME__
password: __PASSWORD__
EOM
```

### Usage

```bash
 ❯ ./gradlew run
:compileJava UP-TO-DATE
:compileGroovy UP-TO-DATE
:processResources UP-TO-DATE
:classes UP-TO-DATE
:run
[main] INFO com.netflix.spinnaker.jira.Triage - Fetching 407 issues
[main] INFO com.netflix.spinnaker.jira.Triage - Fetched 407 issues

...
```

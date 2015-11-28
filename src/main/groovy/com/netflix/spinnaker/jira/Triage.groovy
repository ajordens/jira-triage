package com.netflix.spinnaker.jira

import com.atlassian.jira.rest.client.api.JiraRestClient
import com.atlassian.jira.rest.client.api.domain.Issue
import com.atlassian.jira.rest.client.api.domain.IssueFieldId
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue
import com.atlassian.jira.rest.client.api.domain.input.FieldInput
import com.atlassian.jira.rest.client.api.domain.input.IssueInput
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import groovy.util.logging.Slf4j

@Slf4j
class Triage {
  static void main(String... args) {
    def config = loadConfig(new File(System.getProperty("user.home") + "/.jira.d/", "config.yml"))
    def client = buildJiraRestClient(config)

    fetchIssues(config, client).each { Issue issue ->
      def created = issue.creationDate.toDate().format("yyyy-MM-dd")
      def reporter = "${issue.reporter.displayName} (${issue.reporter.emailAddress})"
      def key = issue.key.padRight(13)
      def status = issue.status.name.padRight(16)
      def summary = issue.summary
      def description = issue.description

      println """
________________________________________________________________________________________________________________________

Created:     Reporter:
${created}   ${reporter}

Key:         Status:         Summary:
${[key, status, summary].join("")}

Description:
${description}

F1, F2, F3 - Feature
B1, B2, B3 - Bug
NF         - Needs Followup
S          - Skip
---
Decision?
"""

      def issueContext = [
          labels: issue.labels,
          priority: issue.priority.id
      ]

      System.in.newReader().readLine()?.split(" ")?.each { String decision ->
        Actions.valueOf(decision.toUpperCase()).action.call(config, issueContext)
      }

      if (issueContext.labels || issueContext.priority) {
        client.issueClient.updateIssue(
            issue.key,
            IssueInput.createWithFields(
                new FieldInput(IssueFieldId.LABELS_FIELD, issueContext.labels),
                new FieldInput(IssueFieldId.PRIORITY_FIELD, ComplexIssueInputFieldValue.with("id", issueContext.priority.toString()))
            )
        )
      }
    }

    client.close()
  }

  static TriageConfig loadConfig(File triageConfigFile) {
    def mapper = new ObjectMapper(new YAMLFactory())
    return mapper.readValue(triageConfigFile, TriageConfig)
  }

  static JiraRestClient buildJiraRestClient(TriageConfig triageConfig) {
    def restClientFactory = new AsynchronousJiraRestClientFactory()
    return restClientFactory.createWithBasicHttpAuthentication(
        new URI(triageConfig.endpoint), triageConfig.username, triageConfig.password
    )
  }

  static Collection<Issue> fetchIssues(TriageConfig triageConfig, JiraRestClient jiraRestClient) {
    def query = """
project = ${triageConfig.project} AND
resolution = Unresolved AND
(labels not in (${triageConfig.tagPrefix}-triaged) OR labels is EMPTY)
""".toString()

    def allIssues = []
    def searchResult = jiraRestClient.searchClient.searchJql(query, 50, 0, null).get()

    log.info("Fetching ${searchResult.total} issues")
    while (allIssues.size() < searchResult.total) {
      allIssues.addAll(searchResult.issues)
      searchResult = jiraRestClient.searchClient.searchJql(query, 50, allIssues.size(), null).get()
    }
    log.info("Fetched ${allIssues.size()} issues")

    return allIssues
  }

  static enum Actions {
    F1("Feature", { TriageConfig triageConfig, Map issueContext ->
      issueContext.labels << "${triageConfig.tagPrefix}-triaged"
      issueContext.labels << "${triageConfig.tagPrefix}-feature"
      issueContext.priority = 2
    }),
    F2("Feature", { TriageConfig triageConfig, Map issueContext ->
      issueContext.labels << "${triageConfig.tagPrefix}-triaged"
      issueContext.labels << "${triageConfig.tagPrefix}-feature"
      issueContext.priority = 3
    }),
    F3("Feature", { TriageConfig triageConfig, Map issueContext ->
      issueContext.labels << "${triageConfig.tagPrefix}-triaged"
      issueContext.labels << "${triageConfig.tagPrefix}-feature"
      issueContext.priority = 4
    }),
    B1("Bug", { TriageConfig triageConfig, Map issueContext ->
      issueContext.labels << "${triageConfig.tagPrefix}-triaged"
      issueContext.labels << "${triageConfig.tagPrefix}-bug"
      issueContext.priority = 2
    }),
    B2("Bug", { TriageConfig triageConfig, Map issueContext ->
      issueContext.labels << "${triageConfig.tagPrefix}-triaged"
      issueContext.labels << "${triageConfig.tagPrefix}-bug"
      issueContext.priority = 3
    }),
    B3("Bug", { TriageConfig triageConfig, Map issueContext ->
      issueContext.labels << "${triageConfig.tagPrefix}-triaged"
      issueContext.labels << "${triageConfig.tagPrefix}-bug"
      issueContext.priority = 4
    }),
    NF("Needs Followup", { TriageConfig triageConfig, Map issueContext ->
      issueContext.labels << "${triageConfig.tagPrefix}-triaged"
      issueContext.labels << "${triageConfig.tagPrefix}-needs-followup"
    }),
    S("Skip", { TriageConfig triageConfig, Map issueContext ->
      // do nothing
    })

    String group
    Closure action

    Actions(String group, Closure action) {
      this.group = group
      this.action = action
    }
  }

  static class TriageConfig {
    String endpoint
    String project
    String username
    String password

    String tagPrefix = "det"
  }
}

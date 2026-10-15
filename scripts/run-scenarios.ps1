# Runs one demo scenario (or all) with a simulated human answering the approval checkpoints.
# Evidence is written to docs\runs\<scenario>.
#
#   .\scripts\run-scenarios.ps1 [-Scenario greenfield|brownfield|ambiguous|failure-build|failure-boundary|failure-secret|rejection|all]
param([string]$Scenario = "all")
Set-Location (Join-Path $PSScriptRoot "..")
mvn -B -q -pl orchestrator spring-boot:run "-Dspring-boot.run.profiles=scenario" "-Dspring-boot.run.arguments=--agentic.scenario=$Scenario"
exit $LASTEXITCODE

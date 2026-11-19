# Shortcuts for building TrojanRideshare and running its tests.
# Every target just calls the Gradle wrapper, so `./gradlew <task>` (or `gradlew.bat <task>`
# on Windows) works the same way without make.
#
# Needs: JDK 17+, and the Android SDK (open the project once in Android Studio, which writes
# local.properties, or set ANDROID_HOME). UI tests also need a running emulator or device.
#
# Run one test class (or one method) with CLASS=, for example:
#   make test-unit CLASS=com.team23.trojanrideshare.service.RideSearchEngineTest
#   make test-ui   CLASS=com.team23.trojanrideshare.ui.search.RideSearchTest
#   make test-ui   CLASS=com.team23.trojanrideshare.ui.search.RideSearchTest#movingRadiusSliderUpdatesResultsLive

GRADLE := ./gradlew
CLASS ?=
REPORTS := app/build.nosync/reports

.DEFAULT_GOAL := help
.PHONY: help build test test-unit test-ui test-all coverage clean

help:
	@echo "make build      Build the debug app (APK in app/build.nosync/outputs/apk/debug)"
	@echo "make test-unit  White-box tests (JUnit, on this computer, no emulator)"
	@echo "make test-ui    Black-box tests (Espresso, needs a running emulator or device)"
	@echo "make test-all   Both of the above"
	@echo "make coverage   White-box tests plus a JaCoCo line/branch coverage report"
	@echo "make clean      Delete build output"
	@echo ""
	@echo "Add CLASS=<fully.qualified.TestClass> to run only one test class (see the top of this file)."

build:
	$(GRADLE) :app:assembleDebug

test: test-unit

test-unit:
	$(GRADLE) :app:testDebugUnitTest $(if $(CLASS),--tests '$(CLASS)')
	@echo "Report: $(REPORTS)/tests/testDebugUnitTest/index.html"

test-ui:
	$(GRADLE) :app:connectedDebugAndroidTest $(if $(CLASS),-Pandroid.testInstrumentationRunnerArguments.class=$(CLASS))
	@echo "Report: $(REPORTS)/androidTests/connected/debug/index.html"

test-all: test-unit test-ui

coverage:
	$(GRADLE) :app:createDebugUnitTestCoverageReport
	@echo "Coverage report: $(REPORTS)/coverage/test/debug/index.html"

clean:
	$(GRADLE) clean

// Offline smoke checks for the exact browser script; never contacts the backend.
const fs = require("node:fs");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const path = require("node:path");

const calls = [];
const storage = new Map();
const categories = new Map();
const exercises = new Map();
let scenario = "open";
let nextId = 901;
let fixtureStatus = "IN_PROGRESS";
const reply = (status, data) => ({ status, redirected: false,
    text: async () => data == null ? "" : JSON.stringify(data) });
const sandbox = {
    console: { log() {}, table() {}, error() {} },
    sessionStorage: { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value) },
    AbortController, setTimeout, clearTimeout, performance, URL,
    location: { protocol: "http:", origin: "http://localhost:8081" },
    document: { querySelector: () => null, hidden: false },
    DOMParser: class {
        parseFromString() {
            return { querySelector: selector => ({ value: selector.includes("_csrf") ? "fake-csrf" : "65" }) };
        }
    },
    fetch: async (url, options) => {
        calls.push({ url, method: options.method, body: options.body });
        if (url === "/profile") return {
            status: 200, redirected: false, text: async () => "<html>mock profile</html>"
        };
        if (scenario.startsWith("close-") && url === "/workouts/current") {
            return fixtureStatus === "IN_PROGRESS"
                ? reply(200, { workoutId: 1, userId: 20, status: fixtureStatus }) : reply(404, null);
        }
        if (scenario.startsWith("close-") && url === "/workouts/1") return reply(200, {
            workoutId: 1, userId: 20, status: fixtureStatus,
            endTime: fixtureStatus === "IN_PROGRESS" ? null : "2026-11-01T22:00:00+07:00"
        });
        if (scenario.startsWith("close-") && url === "/workouts/1/details") {
            return reply(200, scenario === "close-results"
                ? [{ status: "COMPLETED" }, { status: "NOT_STARTED" }] : [{ status: "NOT_STARTED" }]);
        }
        if (scenario.startsWith("close-") && options.method === "PATCH" && url.startsWith("/workouts/1/")) {
            const action = scenario === "close-results" ? "complete" : "cancel";
            assert.equal(url, `/workouts/1/${action}`);
            const status = action === "complete" ? "COMPLETED" : "CANCELLED";
            if (scenario !== "close-unpersisted") fixtureStatus = status;
            return reply(200, { workoutId: 1, userId: 20, status, endTime: "2026-11-01T22:00:00+07:00" });
        }
        if (url === "/workouts/4" && scenario === "cleanup-unmapped") {
            return reply(404, { message: "No static resource workouts/4." });
        }
        if (url === "/workouts/4" && scenario.startsWith("cleanup-")) return reply(200, {
            workoutId: 4, userId: 11, status: fixtureStatus,
            endTime: fixtureStatus === "CANCELLED" ? "2026-11-01T21:00:00+07:00" : null
        });
        if (url === "/workouts/4/cancel" && scenario.startsWith("cleanup-")) {
            if (scenario === "cleanup-good") fixtureStatus = "CANCELLED";
            return reply(200, { workoutId: 4, userId: 11, status: "CANCELLED" });
        }
        if (url === "/workouts/current" && scenario === "missing-route") {
            return reply(404, { status: 404, message: "No static resource workouts/current." });
        }
        if (url === "/workouts/current" && scenario !== "open") return reply(404, null);
        if (url === "/workouts/current" || url === "/workouts/99") return {
            status: 200, redirected: false,
            text: async () => JSON.stringify({ workoutId: 99, userId: 7, status: "IN_PROGRESS" })
        };
        if (url === "/admin/__fitness_workout_permission_probe__") return reply(scenario === "seed" ? 404 : 403, null);
        if (url === "/workouts?page=0&size=1") return reply(200, { content: [], totalElements: 0 });
        if (url === "/check-ins/summary") return reply(200, { today: "2026-11-01", currentStreak: 0,
            highestStreak: 0, consecutiveMissedDays: 0, checkedInToday: false });
        if (url === "/badges" || url === "/badges/mine") return reply(200, []);
        if (url === "/exercises") return reply(200, [...exercises.values()]);
        if (scenario === "seed" && options.method === "POST" && url === "/admin/exercise-categories") {
            const category = { ...JSON.parse(options.body), categoryId: nextId++, active: true };
            categories.set(category.categoryId, category); return reply(201, category);
        }
        if (scenario === "seed" && options.method === "POST" && url === "/admin/exercises") {
            const exercise = { ...JSON.parse(options.body), exerciseId: nextId++, active: true };
            exercises.set(exercise.exerciseId, exercise); return reply(201, exercise);
        }
        if (url.startsWith("/exercise-categories/")) return reply(200, categories.get(Number(url.split("/").pop())));
        if (url.startsWith("/exercises/")) return reply(200, exercises.get(Number(url.split("/").pop())));
        throw new Error(`Unexpected mocked request: ${options.method} ${url}`);
    }
};

(async () => {
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, "workout-module-console.js"), "utf8"), sandbox);
    let api = sandbox.FitnessModuleTest;
    for (const name of ["run", "testForeignAccess", "testAnonymous", "cleanup", "stop", "report", "context", "seedUserFixtures", "userFixtures", "closeExistingWorkout"]) {
        assert.equal(typeof api[name], "function");
    }
    assert.equal(calls.length, 0, "Loading the script must not call any API");

    const invalidMode = await api.run({ mode: "invalid" });
    assert.equal(invalidMode.FAIL, 1);
    assert.equal(calls.length, 0, "Invalid configuration must not call any API");

    const activeWorkout = await api.run({ mode: "admin" });
    assert.equal(activeWorkout.FAIL, 1);
    assert.equal(api.running, false);
    assert.equal(api.context(), null, "Existing data must not be adopted as test fixtures");
    assert.equal(calls.length, 2);
    assert(calls.every(call => call.method === "GET"), "Existing open workout must not trigger writes");
    assert.deepEqual(calls.map(call => call.url), ["/profile", "/workouts/current"]);

    calls.length = 0;
    const ownWorkout = await api.testForeignAccess({ workoutId: 99, detailId: 1 });
    assert.equal(ownWorkout.FAIL, 1);
    assert.deepEqual(calls.map(call => call.url), ["/profile", "/workouts/99"]);
    assert(calls.every(call => call.method === "GET"), "Foreign tests must stop if ownership is not established");
    assert.equal(api.running, false);

    calls.length = 0;
    scenario = "missing-route";
    const missingRoute = await api.run({ mode: "user" });
    assert.equal(missingRoute.FAIL, 1);
    assert.equal(missingRoute.PASS, 1, "An unmapped /current must not pass as no open workout");
    assert.deepEqual(calls.map(call => call.url), ["/profile", "/workouts/current"]);
    assert(calls.every(call => call.method === "GET"));

    calls.length = 0;
    scenario = "missing";
    storage.set("fitness.workout.module-test.v1", JSON.stringify({ prefix: "TEST_WT_old",
        categories: [], exercises: [], workouts: [], cleaned: false, finished: false }));
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, "workout-module-console.js"), "utf8"), sandbox);
    api = sandbox.FitnessModuleTest;
    const missingData = await api.run({ mode: "user" });
    assert.equal(missingData.FAIL, 1);
    assert(calls.some(call => call.url === "/exercises"), "Empty legacy manifest must not block the next run");
    assert(calls.every(call => call.method === "GET"), "Missing exercise data must not cause writes");
    assert.equal(api.context(), null, "Failure before fixtures must not leave an unfinished manifest");
    calls.length = 0;
    const deniedSeed = await api.seedUserFixtures();
    assert.equal(deniedSeed.FAIL, 1);
    assert(calls.every(call => call.method === "GET"), "User must not create seed fixtures");
    assert.equal(api.userFixtures(), null);

    calls.length = 0;
    scenario = "seed";
    const seeded = await api.seedUserFixtures();
    assert.equal(seeded.FAIL, 0);
    assert.equal(api.userFixtures().ready, true);
    assert.equal(api.userFixtures().exerciseIds.length, 3);
    assert.equal(calls.filter(call => call.method === "POST").length, 4);
    assert.deepEqual([...exercises.values()].map(item => item.minDurationMinutes), [1, 0.1, 0.1]);
    calls.length = 0;
    const reused = await api.seedUserFixtures();
    assert.equal(reused.FAIL, 0);
    assert.equal(calls.filter(call => call.method === "POST").length, 0, "Repeat seed must reuse existing fixtures");

    for (const name of ["cleanup-unmapped", "cleanup-lying", "cleanup-good"]) {
        calls.length = 0; scenario = name; fixtureStatus = "IN_PROGRESS";
        storage.set("fitness.workout.module-test.v1", JSON.stringify({ prefix: "TEST_WT_cleanup",
            ownerUserId: 11, categories: [], exercises: [], workouts: [{ id: 4, userId: 11 }],
            cleaned: true, finished: false }));
        vm.runInNewContext(fs.readFileSync(path.join(__dirname, "workout-module-console.js"), "utf8"), sandbox);
        api = sandbox.FitnessModuleTest;
        const result = await api.cleanup();
        assert.equal(result.FAIL, name === "cleanup-good" ? 0 : 1);
        assert.equal(api.context().cleaned, name === "cleanup-good");
        assert.equal(api.context().workouts[0].id, 4, "Cleanup failure must preserve the fixture ID");
        if (name === "cleanup-unmapped") assert(calls.every(call => call.method === "GET"));
        else assert.equal(calls.filter(call => call.url === "/workouts/4").length, 2,
            "Cleanup must read back after cancel instead of trusting the PATCH response alone");
    }
    for (const name of ["close-empty", "close-results", "close-unpersisted"]) {
        calls.length = 0; scenario = name; fixtureStatus = "IN_PROGRESS";
        const beforeContext = JSON.stringify(api.context());
        const result = await api.closeExistingWorkout(1);
        assert.equal(result.FAIL, name === "close-unpersisted" ? 1 : 0);
        assert.equal(JSON.stringify(api.context()), beforeContext, "Closing old workout must not adopt/overwrite another user's manifest");
        assert.equal(calls.filter(call => call.url === "/workouts/1").length, 2);
        if (name !== "close-unpersisted") {
            calls.length = 0;
            assert.equal((await api.closeExistingWorkout(1)).FAIL, 0);
            assert(calls.every(call => call.method === "GET"), "Already closed workout must not be changed again");
        }
    }
    console.log("PASS: mocked guards/seed/cleanup and explicit close-existing checks, including read-back and preserving the fixture manifest.");
})().catch(error => { console.error(error); process.exitCode = 1; });

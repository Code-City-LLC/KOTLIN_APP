package com.ga.airdrop.feature.homedetails

/**
 * WORK ORDER R1 (ORC msg 15293): the previous version of this test cemented
 * the 22657cf reversion — it asserted the Notifications screen renders NO
 * backend inbox (`assertNoBackendInboxSurface`). That "Swift parity" claim was
 * measured against a STALE Swift checkout; current Swift origin/main ships a
 * live inbox (FigmaNotificationsListViewController.swift:370 fetch, :328
 * conditional empty state, :617 markRead, :625 deep-link routing). The live
 * inbox screen has been restored from 22657cf~1.
 *
 * TODO(device lane): author populated-list parity assertions against the
 * restored surface — list rows render from GET /user/notifications, per-type
 * icons per ledger C5 (ShipmentStatusCatalog), tap → markRead + deep-link,
 * empty state ONLY when the backend returns zero rows (mirror Swift
 * fixtureNotifications). Requires an authenticated emulator session (P5
 * queue). Do NOT reintroduce assertions that the inbox is absent.
 */
class NotificationsParityTest

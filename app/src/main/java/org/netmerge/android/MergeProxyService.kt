package org.netmerge.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runs a local SOCKS5 proxy (127.0.0.1:10808) and balances outgoing connections across Wi-Fi
 * and mobile data. Does NOT occupy Android's VpnService slot, allowing RethinkDNS or another VPN
 * to run simultaneously as the system VPN.
 */
class MergeProxyService : Service() {

    companion object {
        const val ACTION_START = "org.netmerge.android.PROXY_START"
        const val ACTION_STOP = "org.netmerge.android.PROXY_STOP"
        private const val TAG = "NetMergeProxy"
        private const val CHANNEL = "proxy_status"
        private const val NOTIF_ID = 2

        private const val NETWORK_WAIT_MS = 3000L

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, MergeProxyService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, MergeProxyService::class.java).setAction(ACTION_STOP))
        }
    }

    private var tracker: NetworkTracker? = null
    private var ticks = 0
    private val main = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            Engine.sample()
            if (ticks++ % 2 == 0) updateNotification()
            main.postDelayed(this, 1000)
        }
    }
    private val establishAnyway = Runnable { establish() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        goForeground()
        if (Engine.phase.value == Engine.Phase.OFF) begin()
        return START_STICKY
    }

    private fun begin() {
        Engine.setPhase(Engine.Phase.STARTING)
        Engine.loadSettings(this)
        requestTileUpdate()
        val t = NetworkTracker(this) { nets -> main.post { onNetworks(nets) } }
        tracker = t
        t.start()
        main.postDelayed(establishAnyway, NETWORK_WAIT_MS)
    }

    private fun establish() {
        main.removeCallbacks(establishAnyway)
        if (Engine.phase.value != Engine.Phase.STARTING) return
        val nets = tracker?.current().orEmpty()

        pushNetworks(nets)
        try {
            val listenAddr = "${Engine.PROXY_HOST}:${Engine.PROXY_PORT}"
            netmerge.Netmerge.startProxy(listenAddr)
            Log.i(TAG, "SOCKS5 proxy started on $listenAddr")
        } catch (e: Exception) {
            Log.e(TAG, "engine proxy start failed", e)
            shutdown()
            return
        }
        Engine.setPhase(Engine.Phase.ON)
        main.post(refresh)
        requestTileUpdate()
    }

    private fun onNetworks(nets: List<NetworkTracker.Tracked>) {
        when (Engine.phase.value) {
            Engine.Phase.OFF -> return
            Engine.Phase.STARTING -> {
                pushNetworks(nets)
                if (nets.isNotEmpty()) establish()
            }
            Engine.Phase.ON -> {
                pushNetworks(nets)
                updateNotification()
            }
        }
    }

    private fun pushNetworks(nets: List<NetworkTracker.Tracked>) {
        val arr = JSONArray()
        for (n in nets) {
            arr.put(
                JSONObject()
                    .put("name", n.name)
                    .put("handle", n.network.networkHandle.toString())
                    .put("v4", n.v4)
                    .put("v6", n.v6)
                    .put("downKbps", n.downKbps),
            )
        }
        runCatching { netmerge.Netmerge.setNetworks(arr.toString()) }
            .onFailure { Log.e(TAG, "setNetworks", it) }
    }

    private fun shutdown() {
        main.removeCallbacks(refresh)
        main.removeCallbacks(establishAnyway)
        if (Engine.phase.value == Engine.Phase.ON) {
            runCatching { netmerge.Netmerge.stopProxy() }
        }
        Engine.setPhase(Engine.Phase.OFF)
        tracker?.stop()
        tracker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        requestTileUpdate()
    }

    override fun onDestroy() {
        if (Engine.phase.value != Engine.Phase.OFF) shutdown()
        super.onDestroy()
    }

    private fun requestTileUpdate() {
        TileService.requestListeningState(this, ComponentName(this, MergeTileService::class.java))
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "net-merge proxy status", NotificationManager.IMPORTANCE_LOW),
        )
        val n = buildNotification("SOCKS5 Proxy: ${Engine.PROXY_HOST}:${Engine.PROXY_PORT} · Waiting for networks")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } catch (e: SecurityException) {
                startForeground(NOTIF_ID, n)
            }
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MergeProxyService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_merge)
            .setContentTitle("net-merge SOCKS5 Proxy (${Engine.PROXY_HOST}:${Engine.PROXY_PORT})")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build()
    }

    private fun updateNotification() {
        if (Engine.phase.value != Engine.Phase.ON) return
        val stats = Engine.stats.value
        val text = if (stats.isEmpty()) {
            "SOCKS5: ${Engine.PROXY_HOST}:${Engine.PROXY_PORT} · Waiting for networks"
        } else {
            "SOCKS5: ${Engine.PROXY_HOST}:${Engine.PROXY_PORT}\n" +
                stats.joinToString("\n") { "${it.name}: ${Stats.rate(it.rateIn + it.rateOut)} · ${it.status}" }
        }
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }
}

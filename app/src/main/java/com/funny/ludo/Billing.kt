package com.funny.ludo

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/** Google Play Billing: coin and diamond packs are consumable in-app products. */
class Billing(private val ctx: Context, private val profile: Profile) {
    private val details = HashMap<String, ProductDetails>()
    private var client: BillingClient? = null
    private var ready = false
    var onMessage: ((String) -> Unit)? = null
    var onPurchase: ((String) -> Unit)? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (p in purchases) handle(p)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            onMessage?.invoke("Purchase failed")
        }
    }

    fun connect() {
        if (!Config.BILLING_ENABLED || client != null) return
        val c = try {
            BillingClient.newBuilder(ctx)
                .setListener(listener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build()
        } catch (_: Throwable) {
            return
        }
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                    ready = true
                    queryDetails()
                    restore()
                }
            }

            override fun onBillingServiceDisconnected() {
                ready = false
                client = null
            }
        })
    }

    private fun queryDetails() {
        val c = client ?: return
        val list = Config.PACKS.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(BillingClient.ProductType.INAPP).build()
        }
        c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build()) { r, items ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                for (d in items) details[d.productId] = d
            }
        }
    }

    /** price text from Play, e.g. "$0.99"; "" when not available yet */
    fun price(id: String): String = details[id]?.oneTimePurchaseOfferDetails?.formattedPrice ?: ""

    fun buy(act: Activity, id: String) {
        val c = client
        val d = details[id]
        if (c == null || !ready || d == null) {
            onMessage?.invoke("Store not available yet. Try again in a moment.")
            connect()
            return
        }
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        c.launchBillingFlow(act, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p)).build())
    }

    /** gives purchases that finished while the app was closed */
    private fun restore() {
        val c = client ?: return
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) for (p in list) handle(p)
        }
    }

    private fun handle(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val c = client ?: return
        c.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r, _ ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                for (pid in p.products) {
                    val pack = Config.PACKS.firstOrNull { it.id == pid } ?: continue
                    if (pack.coins > 0) profile.addCoins(pack.coins)
                    if (pack.diamonds > 0) profile.addDiamonds(pack.diamonds)
                    onMessage?.invoke("Added " + pack.title + "!")
                    onPurchase?.invoke(pid)
                }
            }
        }
    }

    fun destroy() {
        try {
            client?.endConnection()
        } catch (_: Throwable) {
        }
        client = null
    }
}

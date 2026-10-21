package com.example.fanir

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*

class MainActivity : Activity() {
    private var ir: ConsumerIrManager? = null
    private lateinit var prefs: SharedPreferences
    private val h = Handler(Looper.getMainLooper())
    private lateinit var addr: EditText
    private lateinit var cmd: EditText
    private lateinit var status: TextView
    private lateinit var spinner: Spinner
    private var auto = false

    private val names = listOf(
        "Apagar", "Luz", "Noche", "WW (calida)", "NW (neutra)", "W (fria)",
        "Brillo -", "Brillo +", "CCT -", "CCT +", "Ventilador",
        "Vel 1", "Vel 2", "Vel 3", "1h", "2h", "4h"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // NEC: 38 kHz. addr de 2 digitos hex = 8 bits (+ inverso); de 3-4 digitos = 16 bits.
    private fun send(a: String, c: String) {
        try {
            val av = a.toInt(16); val cv = c.toInt(16) and 0xFF
            val first = if (a.length <= 2) listOf(av and 0xFF, av.inv() and 0xFF)
                        else listOf(av and 0xFF, (av shr 8) and 0xFF)
            val bytes = first + listOf(cv, cv.inv() and 0xFF)
            val p = ArrayList<Int>()
            p.add(9000); p.add(4500)
            for (b in bytes) for (i in 0..7) { p.add(560); p.add(if ((b shr i) and 1 == 1) 1690 else 560) }
            p.add(560)
            ir?.transmit(38000, p.toIntArray())
        } catch (e: Exception) { status.text = "Error: ${e.message}" }
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }

    private val step = object : Runnable {
        override fun run() {
            if (!auto) return
            send(addr.text.toString(), cmd.text.toString())
            status.text = "Probando dir ${addr.text} cmd ${cmd.text}"
            move(1)
            h.postDelayed(this, 900)
        }
    }

    private fun move(d: Int) {
        val v = ((cmd.text.toString().toIntOrNull(16) ?: 0) + d) and 0xFF
        cmd.setText("%02X".format(v))
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = getSharedPreferences("codes", MODE_PRIVATE)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(24), dp(12), dp(12)) }
        status = TextView(this)
        if (ir == null || ir?.hasIrEmitter() != true) status.text = "Este telefono no tiene emisor IR"
        else status.text = "Listo. Configura primero los botones abajo."

        root.addView(TextView(this).apply { text = "Fan Light IR"; textSize = 22f })
        root.addView(status)

        // Mando
        var row: LinearLayout? = null
        names.forEachIndexed { i, n ->
            if (i % 3 == 0) { row = LinearLayout(this); root.addView(row) }
            row!!.addView(btn(n) {
                val c = prefs.getString(n, null)
                if (c == null) status.text = "'$n' sin configurar"
                else { val (a, k) = c.split(","); send(a, k); status.text = "Enviado: $n" }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        // Configuracion
        root.addView(TextView(this).apply { text = "\nConfigurar (buscar codigos)"; textSize = 18f })
        root.addView(TextView(this).apply { text = "Apunta el emisor IR (parte superior del telefono) al ventilador. Pulsa Auto y detenlo cuando reaccione; usa < para volver al codigo anterior hasta que reaccione de nuevo. Luego elige el boton y Guarda." })
        val r1 = LinearLayout(this)
        addr = EditText(this).apply { setText("00"); hint = "Direccion hex" }
        cmd = EditText(this).apply { setText("00"); hint = "Comando hex" }
        r1.addView(addr, LinearLayout.LayoutParams(0, -2, 1f)); r1.addView(cmd, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(r1)
        val r2 = LinearLayout(this)
        listOf<Pair<String, () -> Unit>>(
            "<" to { move(-1) }, ">" to { move(1) },
            "Enviar" to { send(addr.text.toString(), cmd.text.toString()); status.text = "Enviado ${addr.text}/${cmd.text}" },
            "Sig. dir" to { addr.setText("%02X".format(((addr.text.toString().toIntOrNull(16) ?: 0) + 1) and 0xFF)); cmd.setText("00") }
        ).forEach { (t, f) -> r2.addView(btn(t, f), LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(r2)
        val autoBtn = btn("Auto: iniciar") {}
        autoBtn.setOnClickListener {
            auto = !auto
            autoBtn.text = if (auto) "Auto: detener" else "Auto: iniciar"
            if (auto) h.post(step) else { h.removeCallbacks(step); move(-1) }
        }
        root.addView(autoBtn)
        spinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names) }
        root.addView(spinner)
        root.addView(btn("Guardar codigo en el boton elegido") {
            val n = spinner.selectedItem as String
            prefs.edit().putString(n, "${addr.text},${cmd.text}").apply()
            status.text = "Guardado $n = ${addr.text}/${cmd.text}"
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onDestroy() { auto = false; h.removeCallbacks(step); super.onDestroy() }
}

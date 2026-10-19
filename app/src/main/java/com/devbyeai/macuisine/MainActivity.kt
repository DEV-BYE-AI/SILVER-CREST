package com.devbyeai.macuisine

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.devbyeai.macuisine.serial.PowerBoard
import com.devbyeai.macuisine.serial.SerialManager

class MainActivity : Activity() {

    private val BG = Color.parseColor("#17130F")
    private val PANEL = Color.parseColor("#201A15")
    private val PANEL2 = Color.parseColor("#2A221B")
    private val FG = Color.parseColor("#F4EDE3")
    private val MUTED = Color.parseColor("#B4A797")
    private val ACCENT = Color.parseColor("#E4A04D")
    private val GO = Color.parseColor("#8FC26C")
    private val HOT = Color.parseColor("#E4663B")

    private val TEMPC = intArrayOf(0,37,45,50,55,60,65,70,75,80,85,90,95,100,105,110,115,120,130)
    private val TIME_STEPS = intArrayOf(0,5,10,15,20,30,45,60,90,120,180,300,600,900,1800,2700,3600)

    private var speed = 0
    private var tempLvl = 0
    private var timeS = 0
    private var reverse = false
    private var turbo = false
    private var running = false

    private lateinit var tTime: TextView
    private lateinit var tSpeed: TextView
    private lateinit var tTemp: TextView
    private lateinit var tStatus: TextView
    private lateinit var tFrame: TextView
    private lateinit var goBtn: Button
    private lateinit var cookView: View
    private lateinit var recipesView: View

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (running) {
                if (timeS > 0) timeS-- else running = false
                renderCook()
                sendCurrent()
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SerialManager.start(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        // top bar
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "Ma Cuisine"; setTextColor(FG); textSize = 22f
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
        val tabCook = tab("Cuisiner") { showCook(true) }
        val tabRec = tab("Recettes") { showCook(false) }
        top.addView(brand, lp(0, WRAP_CONTENT, 1f))
        top.addView(tabCook); top.addView(space(8)); top.addView(tabRec)
        root.addView(top, lp(MATCH_PARENT, WRAP_CONTENT))
        root.addView(space(12))

        cookView = buildCook()
        recipesView = buildRecipes()
        root.addView(cookView, lp(MATCH_PARENT, WRAP_CONTENT))
        root.addView(recipesView, lp(MATCH_PARENT, MATCH_PARENT))

        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
        showCook(true)
        renderCook()
        handler.postDelayed(ticker, 1000)
    }

    private fun buildCook(): View {
        val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PANEL); setPadding(dp(18),dp(18),dp(18),dp(18)) }
        val dials = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        tTime = bigNum("0:00")
        tSpeed = bigNum("0")
        tTemp = bigNum("Off")
        dials.addView(dial("Temps", tTime, { stepTime(-1) }, { stepTime(1) }), lp(0, WRAP_CONTENT, 1f))
        dials.addView(dial("Vitesse", tSpeed, { speed = (speed-1).coerceAtLeast(0); turbo=false; afterChange() }, { speed = (speed+1).coerceAtMost(10); turbo=false; afterChange() }), lp(0, WRAP_CONTENT, 1f))
        dials.addView(dial("Temp. °C", tTemp, { tempLvl = (tempLvl-1).coerceAtLeast(0); afterChange() }, { tempLvl = (tempLvl+1).coerceAtMost(18); afterChange() }), lp(0, WRAP_CONTENT, 1f))
        v.addView(dials, lp(MATCH_PARENT, WRAP_CONTENT))
        v.addView(space(16))

        val ctr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val turboBtn = pill("Turbo") { turbo = !turbo; afterChange() }
        val revBtn = pill("Sens inv.") { reverse = !reverse; afterChange() }
        goBtn = Button(this).apply {
            text = "Départ"; setTextColor(Color.parseColor("#152009")); setBackgroundColor(GO)
            setOnClickListener { running = !running; renderCook(); sendCurrent() }
        }
        val tareBtn = pill("Tare") { }
        ctr.addView(turboBtn); ctr.addView(space(10)); ctr.addView(revBtn); ctr.addView(space(10))
        ctr.addView(goBtn, lp(dp(96), dp(56))); ctr.addView(space(10)); ctr.addView(tareBtn)
        v.addView(ctr, lp(MATCH_PARENT, WRAP_CONTENT))
        v.addView(space(14))

        tStatus = TextView(this).apply { setTextColor(MUTED); textSize = 13f; gravity = Gravity.CENTER }
        v.addView(tStatus, lp(MATCH_PARENT, WRAP_CONTENT))
        v.addView(space(10))

        val serialBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#120F0B")); setPadding(dp(14),dp(12),dp(14),dp(12)) }
        val sh = TextView(this).apply { text = "Console série — ${SerialManager.device} @ ${SerialManager.BAUD}"; setTextColor(MUTED); textSize = 11f }
        tFrame = TextView(this).apply { setTextColor(Color.parseColor("#ECC98F")); textSize = 13f; typeface = Typeface.MONOSPACE }
        serialBox.addView(sh); serialBox.addView(space(6)); serialBox.addView(tFrame)
        v.addView(serialBox, lp(MATCH_PARENT, WRAP_CONTENT))
        return v
    }

    private fun buildRecipes(): View {
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 2; setPadding(0, 0, 0, dp(20)) }
        val recipes = RecipeStore.load(this)
        val shown = recipes.take(48)
        for (r in shown) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setBackgroundColor(PANEL)
                setPadding(0,0,0,dp(10)); setOnClickListener {
                    startActivity(Intent(this@MainActivity, GuideActivity::class.java).putExtra("id", r.id))
                }
            }
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                RecipeStore.image(this@MainActivity, r.id)?.let { setImageBitmap(it) } ?: setBackgroundColor(PANEL2)
            }
            card.addView(img, LinearLayout.LayoutParams(MATCH_PARENT, dp(110)))
            val nm = TextView(this).apply { text = r.name; setTextColor(FG); textSize = 14f; setPadding(dp(10),dp(8),dp(10),dp(2)); typeface = Typeface.create("serif", Typeface.BOLD) }
            val meta = TextView(this).apply { text = "${r.duration} min · ${r.level}"; setTextColor(MUTED); textSize = 11f; setPadding(dp(10),0,dp(10),0) }
            card.addView(nm); card.addView(meta)
            val glp = GridLayout.LayoutParams().apply {
                width = 0; height = WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            grid.addView(card, glp)
        }
        scroll.addView(grid)
        return scroll
    }

    private fun showCook(cook: Boolean) {
        cookView.visibility = if (cook) View.VISIBLE else View.GONE
        recipesView.visibility = if (cook) View.GONE else View.VISIBLE
    }

    private fun afterChange() { renderCook(); if (running) sendCurrent() }

    private fun stepTime(d: Int) {
        var i = TIME_STEPS.indexOf(timeS); if (i < 0) i = 0
        i = (i + d).coerceIn(0, TIME_STEPS.size - 1); timeS = TIME_STEPS[i]; afterChange()
    }

    private fun renderCook() {
        tTime.text = fmt(timeS)
        tSpeed.text = if (turbo) "Turbo" else speed.toString()
        tTemp.text = if (tempLvl == 0) "Off" else TEMPC[tempLvl].toString()
        goBtn.text = if (running) "Stop" else "Départ"
        goBtn.setBackgroundColor(if (running) HOT else GO)
        val mode = if (SerialManager.simulated) "SIMULATION (port non accessible)" else "connecté · ${SerialManager.device}"
        tStatus.text = (if (running) "En marche" else "Au repos") + " · $mode"
        tFrame.text = PowerBoard.hex(currentFrame())
    }

    private fun currentFrame(): ByteArray {
        val sp = if (turbo) 10 else speed
        val cmd = PowerBoard.autoCmd(sp, tempLvl, turbo)
        return PowerBoard.frame(cmd, sp, tempLvl, reverse, turbo)
    }

    private fun sendCurrent() {
        // Stream the run frame while running, otherwise stream all-off.
        val f = if (running) currentFrame() else PowerBoard.OFF
        SerialManager.setFrame(f)
    }

    private fun fmt(s: Int): String = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

    // --- tiny view helpers ---
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private fun space(px: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(px), dp(px)) }
    private fun bigNum(t: String) = TextView(this).apply { text = t; setTextColor(FG); textSize = 34f; gravity = Gravity.CENTER; typeface = Typeface.create("serif", Typeface.BOLD) }

    private fun tab(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label; setTextColor(Color.parseColor("#1A1005")); setBackgroundColor(ACCENT); textSize = 13f
        setOnClickListener { onClick() }
    }
    private fun pill(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label; setTextColor(FG); setBackgroundColor(PANEL2); textSize = 12f
        setOnClickListener { onClick() }
    }
    private fun dial(cap: String, numView: TextView, minus: () -> Unit, plus: () -> Unit): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val capV = TextView(this).apply { text = cap; setTextColor(MUTED); textSize = 12f; gravity = Gravity.CENTER }
        col.addView(numView, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(capV, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(Button(this).apply { text = "–"; setTextColor(FG); setBackgroundColor(PANEL2); setOnClickListener { minus() } })
        row.addView(space(8))
        row.addView(Button(this).apply { text = "+"; setTextColor(FG); setBackgroundColor(PANEL2); setOnClickListener { plus() } })
        col.addView(space(8))
        col.addView(row, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        return col
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(ticker); SerialManager.stop() }
}

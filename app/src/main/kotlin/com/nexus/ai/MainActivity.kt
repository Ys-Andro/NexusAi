package com.nexus.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.*
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.Color
import android.view.View
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import kotlin.concurrent.thread
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var chatContainer: LinearLayout
    private lateinit var inputMessage: EditText
    private lateinit var sendButton: Button
    private lateinit var micButton: Button
    private lateinit var scrollView: ScrollView

    // IMPORTANTE:
    // Reemplaza esto por tu NUEVA API Key de OpenRouter.
    // No compartas públicamente esta clave.
    private val OPENROUTER_API_KEY = "AQUI_VA_TU_API_KEY"

    private val MODEL = "gryphe/mythomax-l2-13b"

    private val conversationHistory = JSONArray()

    private var tts: TextToSpeech? = null

    private val SPEECH_REQUEST_CODE = 100
    private val RECORD_AUDIO_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ==============================
        // INICIAR TEXT-TO-SPEECH
        // ==============================

        tts = TextToSpeech(this, this)

        // ==============================
        // SYSTEM PROMPT
        // ==============================

        conversationHistory.put(
            JSONObject()
                .put("role", "system")
                .put(
                    "content",
                    """
                    Nexus, un asistente de IA personal avanzado.

                    Reglas:
                    - Siempre usas el historial de la conversación.
                    - Recuerdas lo que el usuario dijo antes.
                    - Respondes en español de forma natural, directa y con carácter.
                    - Nunca digas que no tienes memoria.
                    - Sé útil, claro y conciso.
                    """.trimIndent()
                )
        )

        // ==============================
        // LAYOUT PRINCIPAL
        // ==============================

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(24, 24, 24, 24)
        }

        // ==============================
        // TITULO
        // ==============================

        val title = TextView(this).apply {
            text = "Nexus"
            textSize = 28f
            setTextColor(Color.parseColor("#00E5FF"))
            setPadding(0, 0, 0, 32)
            gravity = Gravity.CENTER
        }

        // ==============================
        // SCROLLVIEW
        // ==============================

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        chatContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }

        scrollView.addView(chatContainer)

        // ==============================
        // ZONA DE ENTRADA
        // ==============================

        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        // ==============================
        // CAMPO DE TEXTO
        // ==============================

        inputMessage = EditText(this).apply {
            hint = "Escribe o habla..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)

            setBackgroundColor(
                Color.parseColor("#1A1A1A")
            )

            setPadding(24, 20, 24, 20)

            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        // ==============================
        // BOTÓN MICRÓFONO
        // ==============================

        micButton = Button(this).apply {
            text = "🎙"

            setBackgroundColor(
                Color.parseColor("#1F2A33")
            )

            setTextColor(Color.WHITE)

            setOnClickListener {
                startVoiceInput()
            }
        }

        // ==============================
        // BOTÓN ENVIAR
        // ==============================

        sendButton = Button(this).apply {
            text = "Enviar"

            setBackgroundColor(
                Color.parseColor("#00E5FF")
            )

            setTextColor(Color.BLACK)

            setOnClickListener {
                sendMessage()
            }
        }

        inputLayout.addView(inputMessage)
        inputLayout.addView(micButton)
        inputLayout.addView(sendButton)

        mainLayout.addView(title)
        mainLayout.addView(scrollView)
        mainLayout.addView(inputLayout)

        setContentView(mainLayout)

        // ==============================
        // MENSAJE INICIAL
        // ==============================

        addMessage(
            "Nexus",
            "Sistema de voz iniciado. Puedes hablarme o escribir.",
            false
        )
    }

    // =========================================================
    // TEXT-TO-SPEECH
    // =========================================================

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            val result = tts?.setLanguage(
                  Locale.forLanguageTag("es-MX"))

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {

                Toast.makeText(
                    this,
                    "El idioma español no está disponible para el sintetizador de voz",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // =========================================================
    // INICIAR RECONOCIMIENTO DE VOZ
    // =========================================================

    private fun startVoiceInput() {

        // Comprobar permiso del micrófono

        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )

            return
        }

        launchVoiceRecognizer()
    }

    // =========================================================
    // LANZAR RECONOCIMIENTO
    // =========================================================

    private fun launchVoiceRecognizer() {

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            // Español de México
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "es-MX"
            )

            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Habla con Nexus..."
            )

            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
            )

            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )
        }

        try {

            startActivityForResult(
                intent,
                SPEECH_REQUEST_CODE
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "No se encontró un servicio de reconocimiento de voz",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // =========================================================
    // RESULTADO DEL PERMISO
    // =========================================================

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {

                // Permiso concedido
                launchVoiceRecognizer()

            } else {

                Toast.makeText(
                    this,
                    "Nexus necesita permiso para usar el micrófono",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // =========================================================
    // RESULTADO DEL RECONOCIMIENTO DE VOZ
    // =========================================================

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == SPEECH_REQUEST_CODE &&
            resultCode == RESULT_OK &&
            data != null
        ) {

            val results =
                data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )

            if (!results.isNullOrEmpty()) {

                val recognizedText = results[0]

                inputMessage.setText(
                    recognizedText
                )

                inputMessage.setSelection(
                    inputMessage.text.length
                )

                // Enviar automáticamente
                sendMessage()
            }
        }
    }

    // =========================================================
    // ENVIAR MENSAJE
    // =========================================================

    private fun sendMessage() {

        val text =
            inputMessage.text.toString().trim()

        if (text.isEmpty()) {
            return
        }

        // Mostrar mensaje del usuario
        addMessage(
            "Tú",
            text,
            true
        )

        // Limpiar entrada
        inputMessage.text.clear()

        // Desactivar botones mientras Nexus procesa
        sendButton.isEnabled = false
        micButton.isEnabled = false

        // Mensaje temporal
        addMessage(
            "Nexus",
            "Pensando...",
            false
        )

        // Agregar mensaje al historial
        conversationHistory.put(
            JSONObject()
                .put("role", "user")
                .put("content", text)
        )

        // Ejecutar petición fuera del hilo principal
        thread {

            val response = callOpenRouter()

            Handler(
                Looper.getMainLooper()
            ).post {

                // Eliminar "Pensando..."
                if (chatContainer.childCount > 0) {

                    chatContainer.removeViewAt(
                        chatContainer.childCount - 1
                    )
                }

                // Mostrar respuesta
                addMessage(
                    "Nexus",
                    response,
                    false
                )

                // Nexus habla
                speak(response)

                // Agregar respuesta al historial
                conversationHistory.put(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", response)
                )

                // Reactivar botones
                sendButton.isEnabled = true
                micButton.isEnabled = true
            }
        }
    }

    // =========================================================
    // TEXT-TO-SPEECH
    // =========================================================

    private fun speak(text: String) {

        if (text.isBlank()) {
            return
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "NEXUS_RESPONSE"
        )
    }

    // =========================================================
    // OPENROUTER
    // =========================================================

    private fun callOpenRouter(): String {

        return try {

            val limitedHistory = JSONArray()

            // System prompt
            limitedHistory.put(
                conversationHistory.getJSONObject(0)
            )

            // Mantener las últimas 12 interacciones
            val start =
                maxOf(
                    1,
                    conversationHistory.length() - 12
                )

            for (
                i in start until conversationHistory.length()
            ) {

                limitedHistory.put(
                    conversationHistory.getJSONObject(i)
                )
            }

            // URL
            val url = URL(
                "https://openrouter.ai/api/v1/chat/completions"
            )

            val connection =
                url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"

            connection.setRequestProperty(
                "Authorization",
                "Bearer $OPENROUTER_API_KEY"
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "HTTP-Referer",
                "https://nexus-ai.app"
            )

            connection.setRequestProperty(
                "X-Title",
                "Nexus AI"
            )

            connection.doOutput = true

            // ==============================
            // CUERPO DE LA PETICIÓN
            // ==============================

            val body = JSONObject()

            body.put(
                "model",
                MODEL
            )

            body.put(
                "messages",
                limitedHistory
            )

            body.put(
                "temperature",
                0.7
            )

            // ==============================
            // ENVIAR
            // ==============================

            val writer =
                OutputStreamWriter(
                    connection.outputStream
                )

            writer.write(
                body.toString()
            )

            writer.flush()
            writer.close()

            // ==============================
            // RESPUESTA
            // ==============================

            val responseCode =
                connection.responseCode

            val inputStream =
                if (responseCode in 200..299) {

                    connection.inputStream

                } else {

                    connection.errorStream
                }

            val reader =
                BufferedReader(
                    InputStreamReader(inputStream)
                )

            val response =
                reader.readText()

            reader.close()

            // ==============================
            // PROCESAR RESPUESTA
            // ==============================

            if (responseCode in 200..299) {

                val json =
                    JSONObject(response)

                json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

            } else {

                "Error de OpenRouter ($responseCode): $response"
            }

        } catch (e: Exception) {

            "Error de conexión: ${e.message}"
        }
    }

    // =========================================================
    // AGREGAR MENSAJE AL CHAT
    // =========================================================

    private fun addMessage(
        sender: String,
        message: String,
        isUser: Boolean
    ) {

        val messageView =
            TextView(this).apply {

                text =
                    "$sender: $message"

                textSize = 16f

                setTextColor(
                    if (isUser) {
                        Color.WHITE
                    } else {
                        Color.parseColor("#00E5FF")
                    }
                )

                setPadding(
                    20,
                    16,
                    20,
                    16
                )

                setBackgroundColor(
                    if (isUser) {
                        Color.parseColor("#1F2A33")
                    } else {
                        Color.parseColor("#132029")
                    }
                )

                val params =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                params.setMargins(
                    0,
                    8,
                    0,
                    8
                )

                layoutParams = params
            }

        chatContainer.addView(
            messageView
        )

        // Bajar automáticamente
        scrollView.post {
            scrollView.fullScroll(
                View.FOCUS_DOWN
            )
        }
    }

    // =========================================================
    // DESTRUIR ACTIVIDAD
    // =========================================================

    override fun onDestroy() {

        tts?.stop()
        tts?.shutdown()

        super.onDestroy()
    }
}

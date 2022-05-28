package com.example.myapplication3


import android.Manifest
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.*


class MainActivity : AppCompatActivity(), OnInitListener {
    var tts: TextToSpeech? = null
    var sttr = ""
    var resttr = ""
    var movieDataList: ArrayList<SampleData>? = null

    var searchString = "정상화"


    val clist: ArrayList<String> = arrayListOf<String>("")

    var distrub_state: Boolean = false //방해금지 버튼 상태
    var focus_state: Boolean = false //다시듣기 버튼 상태
    var replay_state: Boolean = true
    var messagTx = ""
    var phoneNum: String = ""
    var str = "개인 카톡"

    var focus_subText: String? = null
    var focus_title: String? = null
    private var backKeyPressedTime: Long = 0


    /*stt 변수*/
    private var SttIntent: Intent? = null
    private var mRecognizer: SpeechRecognizer? = null
    private var sttActivated = false
    private var sttCounter = 4 //음성명령을 몇초간 기다리는가
    private val audioManager: AudioManager? = null

    /*음성알림 키워드*/
    private val activationKeyword: String? = "마이"
    private val exitKeyword: String? = "종료"
    private val noDisturbKeyword:String? = "방해 금지"

    /*음성명령 응답*/
    private val activationResponse: String? = "명령을 내려주세요"
    private val exitResponse: String? = "앱을 종료합니다"
    private val noDisturbOnResponse: String? = "방해금지 시작"
    private val noDisturbOffResponse: String = "방해금지 종료"
    private val sttFailedResponse: String? = "인식에 실패했습니다"

    var distrub_event: ImageButton? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startService(Intent(baseContext, ClearService::class.java))

        InitializeMovieData()


        LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, IntentFilter("Msg"))

        tts = TextToSpeech(this@MainActivity, this)

        /*정상화의 귀여운 STT 코드*/
        SttIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        SttIntent!!.putExtra( RecognizerIntent.EXTRA_CALLING_PACKAGE, getApplicationContext().getPackageName() )
        SttIntent!!.putExtra( RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        SttIntent!!.putExtra( RecognizerIntent.EXTRA_MAX_RESULTS, 3)

        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        mRecognizer?.setRecognitionListener(listener)

        if (!permissionGrantred()) {
            val intent = Intent(
                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
            )
            startActivity(intent)
        }

        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS, Manifest.permission.RECORD_AUDIO),
            100
        )


        if (ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            var numbb = getPhoneNumber(searchString, this)
            if (numbb != null) {
                numbb = reNum(numbb)
            }
            Log.d("display name", numbb ?: "NotFound")
        }


        distrub_event = findViewById<ImageButton>(R.id.DistrubButton)
        val focus_event = findViewById<ImageButton>(R.id.FocusButton)
        val text_event = findViewById<ImageButton>(R.id.TextButton)



        distrub_event!!.setOnClickListener {

            distrub_state = !distrub_state //true -> false, false -> true로
            if (distrub_state) {//distrub_state true 라면
                tts!!.stop()
            }
            distrub_event!!.isSelected = !distrub_event!!.isSelected
        }



        focus_event.setOnClickListener {

            if (!focus_state && replay_state) {
                replay_state = false
            } else if (!focus_state && !replay_state) {
                replay_state = true
            } else if (focus_state && replay_state) {
                focus_state = false
            }

            focus_event.isSelected = !focus_event.isSelected
        }


        text_event.setOnClickListener(View.OnClickListener {
            val ad: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
            ad.setIcon(R.drawable.micromanager_icon)
            ad.setTitle("현재 답장 메시지 내용")
            ad.setMessage(messagTx);
            val et = EditText(this@MainActivity)
            ad.setView(et)
            ad.setPositiveButton("변경", DialogInterface.OnClickListener { dialog, which ->
                val result = et.text.toString()
                messagTx = result
                dialog.dismiss()
                clist.clear()
                clist.add("")
            })
            ad.setNegativeButton("취소",
                DialogInterface.OnClickListener { dialog, which -> dialog.dismiss() })
            ad.show()
        })


        /*stt Listening start*/
        with(mRecognizer) {
            this?.startListening(SttIntent)
        };




    }

    private val onNotice: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {


            val listView = findViewById<View>(R.id.listView) as ListView
            val myAdapter = MyAdapter(this@MainActivity, movieDataList)



            val subText = intent.getStringExtra("subText")
            val title = intent.getStringExtra("title")
            val text = intent.getStringExtra("text")
            val check = intent.getStringExtra("clearAll")


            if (Helper.isAppRunning(this@MainActivity,"com.example.myapplication3")){
                if (subText != null) {
                    sttr = "$subText$title$text"
                    movieDataList!!.add(0, SampleData("<$subText>", "$title", "$text"))
                } else {
                    sttr = "$str$title$text"
                    movieDataList!!.add(0, SampleData("<개인 카톡>", "$title", "$text"))

                }



                if (!distrub_state) {// distrub_state false일때 음성 출력

                    if (focus_state) {
                        if (focus_title == null) {
                            if (focus_subText == "<$subText>") {
                                speakJust(sttr)
                                Toast.makeText(
                                    this@MainActivity,
                                    "집중 카톡방 : ${focus_subText}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            if (focus_title == title && subText == null) {
                                speakJust(sttr)
                                Toast.makeText(
                                    this@MainActivity,
                                    "집중 갠톡방 : ${focus_title}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else
                        speakJust(sttr)
                }

                if (distrub_state) {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        && ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (subText == null) {
                            if (cashcheck("$title")) {
                                clist.add("$title")
                                var numThing = getPhoneNumber("$title", this@MainActivity)

                                if (numThing != null) {
                                    numThing = reNum(numThing)
                                    phoneNum = numThing!!
                                    sendSMS()
                                } else
                                    Toast.makeText(this@MainActivity, "번호가 없습니다", Toast.LENGTH_SHORT)
                                        .show()
                            } else
                                Toast.makeText(this@MainActivity, "이미 메시지를 보냈습니다", Toast.LENGTH_SHORT)
                                    .show()

                        }

                    } else {
                        //when permission is not granted
                        //request for permisiion
                        Toast.makeText(this@MainActivity, "권한이 없습니다", Toast.LENGTH_SHORT).show()
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS),
                            100
                        )
                    }


                }


                listView.adapter = myAdapter

                listView.onItemClickListener =
                    OnItemClickListener { a_parent, a_view, a_position, a_id ->
                        val item: SampleData = myAdapter.getItem(a_position) as SampleData

                        if (!distrub_state) {
                            if (!replay_state) {
                                focus_state = true
                                if (item.subText != "<개인 카톡>") {
                                    focus_subText = item.subText
                                    focus_title = null
                                    Toast.makeText(
                                        this@MainActivity,
                                        "${focus_subText}을 집중 카톡방 선택",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    focus_title = item.title
                                    focus_subText = null
                                    Toast.makeText(
                                        this@MainActivity,
                                        "${focus_title}을 집중 갠톡방 선택",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                replay_state = true
                            } else {
                                Toast.makeText(this@MainActivity, "음성 다시 재생", Toast.LENGTH_SHORT).show()
                                if (item.subText != null) {
                                    resttr = "${item.subText}${item.title}${item.text}"
                                } else {
                                    resttr = "$str${item.title}${item.text}"
                                }
                                tts!!.stop()
                                speakJust(resttr)
                            }
                        }
                    }
            }




        }
    }

    private fun muteRecognition(mute: Boolean) {
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val flag: Int
                flag = if (mute) {
                    AudioManager.ADJUST_MUTE
                } else {
                    AudioManager.ADJUST_UNMUTE
                }
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, flag, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM, flag, 0)
            } else {
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, mute)
                audioManager.setStreamMute(AudioManager.STREAM_ALARM, mute)
            }
        }
    }




    fun InitializeMovieData() {
        movieDataList = ArrayList()

    }


    override fun onDestroy() {
        super.onDestroy()
        // Don't forget to shutdown!
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }
        if(mRecognizer != null){
            muteRecognition(false)
            mRecognizer!!.destroy()
            mRecognizer!!.cancel()
            mRecognizer = null
        }
    }

    override fun onInit(status: Int) {
        // TODO Auto-generated method stub
        if (status == TextToSpeech.SUCCESS) {

            // 한국어 설정
            val result = tts!!.setLanguage(Locale.KOREAN)
            // tts.setPitch(5); // set pitch level
            // tts.setSpeechRate(2); // set speech speed rate

            // 한국어가 안된다면,
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language is not supported")
            } else {
                Log.e("TTS", "Success")
            }
        } else {
            Log.e("TTS", "Initilization Failed")
        }
    }

    private fun permissionGrantred(): Boolean {
        val sets = NotificationManagerCompat.getEnabledListenerPackages(this)
        return if (sets != null && sets.contains(packageName)) {
            true
        } else {
            false
        }
    }

    fun speakJust(text: String?) {
        // tts가 사용중이면, 말하지않는다.
        tts!!.speak(text, TextToSpeech.QUEUE_ADD, null)
    }

    companion object {
        private const val TAG = "MainActivity"
    }


    private fun sendSMS() {
        //get value form editText
        var phone: String = phoneNum
        var message: String = messagTx

        //check condition if string is empty or not
        if (!phone.isEmpty() && !message.isEmpty()) {
            //initialize SMS Manager
            val smsManager: SmsManager = SmsManager.getDefault()
            //send message
            smsManager.sendTextMessage(phone, null, message, null, null)
            //display Toast msg
            Toast.makeText(this, "SMS sent successfully", Toast.LENGTH_SHORT).show()
        } else {
            //when string is empty then display toast msg
            Toast.makeText(this, "Please enter phone and message", Toast.LENGTH_SHORT).show()
        }
    }


    fun getPhoneNumber(name: String, context: Context): String? {
        var ret: String? = null
        val selection =
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " like'%" + name + "%'"
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val c = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, null, null
        )
        if (c!!.moveToFirst()) {
            ret = c.getString(0)
        }
        c.close()
        if (ret == null) ret = null
        return ret
    }


    fun reNum(n: String): String? {
        val numb = n.replace("-", "")
        return numb
    }

    fun cashcheck(subText: String): Boolean {

        for (str in clist) {
            if (subText == str) {
                return false
            }
        }
        return true
    }


    var listener: RecognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(bundle: Bundle) {
            muteRecognition(!sttActivated)
        }
        override fun onBeginningOfSpeech() {
            //사용자가 말하기 시작
        }

        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(bytes: ByteArray) {}
        override fun onEndOfSpeech() {
            //사용자가 말을 멈추면 호출
            //인식 결과에 따라 onError나 onResults가 호출됨
        }

        override fun onError(i: Int) {
            if (sttActivated) {
                if (sttCounter < 0) {
                    sttActivated = false
                    sttCounter = 4
                    mRecognizer?.startListening(SttIntent)

                    val map = HashMap<String, String>()
                    map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "OrderComplete"
                    tts!!.setPitch(1.0.toFloat())
                    tts!!.setSpeechRate(1.0.toFloat())
                    tts!!.speak(sttFailedResponse, TextToSpeech.QUEUE_ADD, map)
                } else {
                    sttCounter--
                }
                if (i == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    mRecognizer!!.cancel()
                } else if (i == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    muteRecognition(false)
                    mRecognizer!!.destroy()
                    mRecognizer!!.setRecognitionListener(this)
                }
            }
            mRecognizer!!.startListening(SttIntent)
        }

        override fun onResults(results: Bundle) {


            var key = ""
            key = SpeechRecognizer.RESULTS_RECOGNITION
            val mResult = results.getStringArrayList(key)
            if (mResult != null) {
                if (sttActivated) {
                    Log.d("result", mResult[0])
                    val map = HashMap<String, String>()
                    map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "orderComplete"
                    mRecognizer!!.stopListening()
                    if (mResult.size > 0) {
                        if (mResult[0].contains(noDisturbKeyword.toString())) {
                                Log.d("key assigned", "Disturb Activation")
                                sttActivated = false
                                sttCounter = 4
                                mRecognizer!!.startListening(SttIntent)
                                tts!!.setPitch(1.0.toFloat())
                                tts!!.setSpeechRate(1.0.toFloat())
                            if (!distrub_state) {
                                tts!!.stop()
                                distrub_state=!distrub_state
                                tts!!.speak(noDisturbOnResponse, TextToSpeech.QUEUE_ADD, map)
                                distrub_event!!.isSelected = !distrub_event!!.isSelected
                            }
                            else{
                                tts!!.stop()
                                distrub_state=!distrub_state
                                tts!!.speak(noDisturbOffResponse, TextToSpeech.QUEUE_ADD, map)
                                distrub_event!!.isSelected = !distrub_event!!.isSelected
                            }
                        }
                        else if (mResult[0].contains(exitKeyword.toString())){
                            Log.d("key assigned", "App Exit")
                            sttActivated = false
                            sttCounter = 4
                            mRecognizer!!.startListening(SttIntent)
                            tts!!.setPitch(1.0.toFloat())
                            tts!!.setSpeechRate(1.0.toFloat())
                            tts!!.stop()
                            tts!!.speak(exitResponse, TextToSpeech.QUEUE_ADD, map)
                            moveTaskToBack(true); // 태스크를 백그라운드로 이동
                            finishAndRemoveTask(); // 액티비티 종료 + 태스크 리스트에서 지우기
                            System.exit(0);
                        }
                    }
                    mRecognizer!!.startListening(SttIntent)
                } else {
                    if (mResult.size > 0) {
                        if (mResult[0].contains(activationKeyword.toString())) {
                            Log.d("key assigned", "Start Activation")
                            tts?.stop()
                            sttCounter = 4
                            muteRecognition(false)
                            val map = HashMap<String, String>()
                            map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "OrderStart"
                            if (tts != null) { //tts 있는지 확인
                                tts!!.speak(activationResponse, TextToSpeech.QUEUE_ADD, map)
                            }
                            sttActivated = true
                        }
                    }
                    mRecognizer!!.startListening(SttIntent)
                }
            }
        }

        override fun onPartialResults(bundle: Bundle?) {}

        override fun onEvent(i: Int, bundle: Bundle?) {}

    }


    override fun onBackPressed() {
        if (System.currentTimeMillis() > backKeyPressedTime + 2000) {
            backKeyPressedTime = System.currentTimeMillis()
            return
        }
        if (System.currentTimeMillis() <= backKeyPressedTime + 2000) {
            finishAndRemoveTask(); // 액티비티 종료 + 태스크 리스트에서 지우기
            System.exit(0);
        }
    }









    object Helper {
        fun isAppRunning(context: Context, packageName: String): Boolean {
            val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val procInfos = activityManager.runningAppProcesses
            if (procInfos != null) {
                for (processInfo in procInfos) {
                    if (processInfo.processName == packageName) {
                        return true
                    }
                }
            }
            return false
        }
    }

}
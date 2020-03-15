package pereira.agnaldo.audiorecorder

import android.content.Context
import android.media.MediaPlayer
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.view.isVisible
import com.github.windsekirun.naraeaudiorecorder.NaraeAudioRecorder
import com.github.windsekirun.naraeaudiorecorder.chunk.AudioChunk
import com.github.windsekirun.naraeaudiorecorder.config.AudioRecordConfig
import com.github.windsekirun.naraeaudiorecorder.model.RecordState
import com.github.windsekirun.naraeaudiorecorder.source.NoiseAudioSource
import kotlinx.android.synthetic.main.audio_recorder_layout.view.*
import java.io.File
import java.io.FileInputStream


class AudioRecorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var audioCapture = AudioCapture(0)
    private val mediaPlayer = MediaPlayer()
    private var isMediaPlayerPrepared = false

    private lateinit var audioRecorder: NaraeAudioRecorder
    private val recordFile =
        File(context.cacheDir, "record3.wav")
    private var isRecording = false

    private var recordedAudioDuration = 0L
    private var milliSecondsPerPercentage = 0L
    private var totalRecordedAudioDurationFormatted = "00:00"


    /////////////////////// listeners ////////////////////////
    private var onFinishRecordListener: ((File) -> Unit)? = null
    private var onIFinishRecordListener: OnFinishRecordListener? = null


    fun setOnFinishRecord(file: (file: File) -> Unit) {
        onFinishRecordListener = file
    }

    fun setOnFinishRecord(onIFinishRecordListener: OnFinishRecordListener) {
        this.onIFinishRecordListener = onIFinishRecordListener
    }

    interface OnFinishRecordListener {
        fun onFinishRecordListener(file: File)
    }
    /////////////////////// end listeners ////////////////////////


    init {
        orientation = VERTICAL
        getStyles(attrs, defStyleAttr)

        LayoutInflater.from(context).inflate(R.layout.audio_recorder_layout, this)
        play_button.setOnClickListener { onPlayButtonClicked() }
        pause_button.setOnClickListener { onPauseButtonClicked() }
        stop_button.setOnClickListener { onStopButtonClicked() }
        record_button.setOnClickListener { onRecordButtonClicked() }
        delete_button.setOnClickListener { onDeleteButtonClicked() }

        play_pause_seek.isVisible = false

        play_pause_seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setMediaPlayerProgress(progress)
                }
            }
        })

        audioCapture.setFftListener { bytes -> onFftListenerReceived(bytes) }
    }

    private fun initializeAudioRecorder() {
        val recordConfig = AudioRecordConfig.defaultConfig()
        val audioSource = NoiseAudioSource(recordConfig)

        recordFile.deleteIfExists()

        audioRecorder = NaraeAudioRecorder()
        audioRecorder.create {
            this.destFile = recordFile
            this.recordConfig = recordConfig
            this.audioSource = audioSource
            this.debugMode = true
            timerCountCallback = { currentTime, _ ->
                onTimerCountCallbackReceive(currentTime)
            }
            chunkAvailableCallback = { onAudioRecorderCallbackReceive(it) }
        }
        audioRecorder.setOnRecordStateChangeListener { recordState ->
            onRecordStateChangeListener(recordState)
        }
    }

    private fun getStyles(attrs: AttributeSet?, defStyle: Int) {
//        attrs?.let {
//            val typedArray = context.obtainStyledAttributes(
//                attrs,
//                R.styleable.ImageCollectionView, defStyle, R.style.defaultPreviewImageCollection
//            )
//            pinchToZoom = typedArray.getBoolean(
//                R.styleable.ImageCollectionView_pinchToZoom, pinchToZoom
//            )
//            typedArray.recycle()
//        }
    }

    ////////////////////////////////record/////////////////////////////
    private fun onRecordButtonClicked() {
        play_button.isVisible = false
        pause_button.isVisible = false
        record_button.isVisible = false
        stop_button.isVisible = true
        delete_button.isVisible = false
        horizontal_wave.isVisible = true
        play_pause_seek.isVisible = false

        if (!isRecording) {
            initializeAudioRecorder()
            audioRecorder.startRecording(context)
        }
    }

    private fun onStopButtonClicked() {
        play_button.isVisible = true
        pause_button.isVisible = false
        record_button.isVisible = false
        stop_button.isVisible = false
        delete_button.isVisible = true
        horizontal_wave.isVisible = false
        play_pause_seek.isVisible = true

        recordedAudioDuration = currentRecorderTime
        if (isRecording) {
            isRecording = false
            audioRecorder.stopRecording()
            setMediaPlayerProgress(0)
            play_pause_seek.progress = 0
        }
    }

    private fun onAudioRecorderCallbackReceive(audioChunk: AudioChunk) {
        val byteArray = audioChunk.toByteArray()
        horizontal_wave.updateAudioWave(byteArray)
    }

    private var currentRecorderTime = 0L
    private fun onTimerCountCallbackReceive(currentTime: Long) {
        currentRecorderTime = currentTime
        setTimer((currentTime / 1000).toInt())
    }

    private fun onRecordStateChangeListener(recordState: RecordState) {
        isRecording = recordState == RecordState.START
        if (recordState == RecordState.STOP) {
            onFinishRecordListener?.invoke(recordFile)
            onIFinishRecordListener?.onFinishRecordListener(recordFile)

            recordedAudioDuration = currentRecorderTime
            milliSecondsPerPercentage = recordedAudioDuration / 100
            totalRecordedAudioDurationFormatted =
                getTimeFormatted((recordedAudioDuration / 1000).toInt())
        }
    }


    ///////////////////////////// play pause///////////////////////
    private fun onPlayButtonClicked() {
        play_button.isVisible = false
        pause_button.isVisible = true
        record_button.isVisible = false
        stop_button.isVisible = false
        delete_button.isVisible = true
        horizontal_wave.isVisible = false
        play_pause_seek.isVisible = true

        audioCapture.start()

        if (!isMediaPlayerPrepared) {
            val fis = FileInputStream(recordFile)
            try {
                mediaPlayer.setDataSource(fis.fd)
                mediaPlayer.isLooping = false
                mediaPlayer.prepare()
                mediaPlayer.setOnPreparedListener { onMediaPlayerPrepared() }
                mediaPlayer.setOnCompletionListener { onMediaPlayerCompleted() }
            } catch (ex: Exception) {
                ex.printStackTrace()
            } finally {
                try {
                    fis.close()
                } catch (ex1: Exception) {
                    ex1.printStackTrace()
                }
            }
        } else {
            startMediaPlayer()
        }
    }

    private fun onMediaPlayerPrepared() {
        recordedAudioDuration = mediaPlayer.duration.toLong()
        milliSecondsPerPercentage = recordedAudioDuration / 100
        totalRecordedAudioDurationFormatted =
            getTimeFormatted((recordedAudioDuration / 1000).toInt())

        isMediaPlayerPrepared = true
        setMediaPlayerProgress(0)
        startMediaPlayer()
    }

    private fun updateMediaPlayerProgress() {
        if (mediaPlayer.isPlaying) {
            play_pause_seek.post {
                if (milliSecondsPerPercentage * play_pause_seek.progress < recordedAudioDuration) {
                    setTimer((mediaPlayer.currentPosition / 1000))

                    val progress = play_pause_seek.progress + 1
                    play_pause_seek.progress = progress
                }
            }
            postDelayed({ updateMediaPlayerProgress() }, milliSecondsPerPercentage)
        }
    }

    private fun onMediaPlayerCompleted() {
        onPauseButtonClicked()
        play_pause_seek.progress = 0
        setMediaPlayerProgress(0)
    }

    private fun startMediaPlayer() {
        if (isMediaPlayerPrepared) {
            setMediaPlayerProgress(play_pause_seek.progress)

            mediaPlayer.start()
            audioCapture.start()

            postDelayed({ updateMediaPlayerProgress() }, milliSecondsPerPercentage)
        }
    }

    private fun onPauseButtonClicked() {
        play_button.isVisible = true
        pause_button.isVisible = false
        record_button.isVisible = false
        stop_button.isVisible = false
        delete_button.isVisible = true
        horizontal_wave.isVisible = false
        play_pause_seek.isVisible = true

        audioCapture.stop()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    private fun onFftListenerReceived(bytes: ByteArray?) {}

    ///////////////////////////////////////delete ////////////////////////////
    private fun onDeleteButtonClicked() {
        play_button.isVisible = false
        pause_button.isVisible = false
        record_button.isVisible = true
        stop_button.isVisible = false
        delete_button.isVisible = false
        horizontal_wave.isVisible = true
        play_pause_seek.isVisible = false

        horizontal_wave.clearWave()

        if (isMediaPlayerPrepared) {
            mediaPlayer.stop()
            mediaPlayer.reset()
            isMediaPlayerPrepared = false
        }
        isRecording = false
        setMediaPlayerProgress(0)
        timer_view.post { timer_view.text = "00:00" }
    }

    /////////////////////////timer///////////////////////
    private fun setTimer(seconds: Int) {
        val currentTime = getTimeFormatted(seconds)
        if (!isMediaPlayerPrepared && isRecording) {
            timer_view.post { timer_view.text = currentTime }
        } else {
            timer_view.post {
                val currentProgress = "$currentTime/$totalRecordedAudioDurationFormatted"
                timer_view.text = currentProgress
            }
        }
    }

    private fun getTimeFormatted(seconds: Int): String {
        val timerSeconds = seconds % 60
        val timerMinutes = (seconds - timerSeconds) / 60

        val secondsString = timerSeconds.toString().padStart(2, '0')
        val minutesString = timerMinutes.toString().padStart(2, '0')

        return "$minutesString:$secondsString"
    }

    /////////////////////////// seek progress ///////////////////////
    private fun setMediaPlayerProgress(progress: Int) {
        val seekTo = recordedAudioDuration * (progress / 100f)
        if (isMediaPlayerPrepared) {
            mediaPlayer.seekTo(seekTo.toInt())
        }
        setTimer((seekTo / 1000).toInt())
    }

}

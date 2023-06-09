package com.tp.cameraxemotionrecognition;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private final int CAMERA_PERMISSION_CODE = 1000;
    private final String[] CAMERA_PERMISSION = new String[]{Manifest.permission.CAMERA};
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private ImageView imageView;
    private TextView textView;
    private FaceDetectorOptions faceDetectorOptions;
    private FaceDetector faceDetector;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Layout elemanlarını tanıt
        initComponents();

        // TTS motorunu yapılandır
        initTTS();

        // kamera kullanımı için izin var mı kontrol et
        checkCameraPermission();

        // Configure Face Detector
        faceDetectorOptions = new FaceDetectorOptions.Builder().build();
    }

    private void initComponents(){
        previewView = findViewById(R.id.previewView);
        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);
    }

    private void initTTS(){
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                // Çünkü java Türkçe lokalizasyona sahip değil
                Locale locale = new Locale("tr_TR");

                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(locale);
                }


                boolean isGoogleAvaible=false;
                // Yüklü TTS engine kontrol et
                List engineList = textToSpeech.getEngines();

                for(Object strEngine : engineList){
                    Log.d(TAG, strEngine.toString());

                    // Cihazdaki TTS motorlarında Google var mı kotrol et
                    if(strEngine.toString().equals("EngineInfo{name=com.google.android.tts}")){
                        isGoogleAvaible = true;
                    }
                }

                if(!isGoogleAvaible){
                    Toast.makeText(MainActivity.this, "Google TTS eksik. Yükleme gerekli.", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("market://details?id=com.google.android.tts"));
                    startActivity(intent);
                }

                //google tts yoksa veya verisi eksikse
                int code = textToSpeech.isLanguageAvailable(locale);
                if (code == TextToSpeech.LANG_NOT_SUPPORTED || code == TextToSpeech.LANG_MISSING_DATA) {
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(installIntent);
                }
            }
        }, "com.google.android.tts");
    }

    private void startCamera(){
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // lifecycle bind için
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview'in kendisi
                Preview preview = new Preview.Builder().build();

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // ImageAnalyzer use case builder
                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // https://stackoverflow.com/questions/58373986/takepicture-require-executor-on-camerax-1-0-0-alpha06
                Executor executor = Executors.newSingleThreadExecutor();
                faceDetector = FaceDetection.getClient(faceDetectorOptions);
                FaceDetectorAnalyzer faceDetectorAnalyzer = new FaceDetectorAnalyzer(
                        faceDetector,
                        this,
                        imageView,
                        textView,
                        textToSpeech);
                imageAnalyzer.setAnalyzer(executor, faceDetectorAnalyzer);

                // Default olarak arka kamerayı al
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // unbind before rebind (pop before push gibi)
                cameraProvider.unbindAll();

                // bind uses cases (owner, cameraselector, usecase, usecase, usecase)
                cameraProvider.bindToLifecycle(MainActivity.this, cameraSelector, preview, imageAnalyzer);

            } catch (InterruptedException | ExecutionException e) {
               Log.e(TAG, "Lifecycle bind failed", e);
            }

        }, ContextCompat.getMainExecutor(this));
    }

    private void checkCameraPermission(){
        if(ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, CAMERA_PERMISSION, CAMERA_PERMISSION_CODE);
        } else{
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == CAMERA_PERMISSION_CODE){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                startCamera();
            }
            else{
                Toast.makeText(this, "Kullanıcı kameraya izin vermedi.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if(textToSpeech != null){
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
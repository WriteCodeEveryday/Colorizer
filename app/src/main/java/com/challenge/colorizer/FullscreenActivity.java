package com.challenge.colorizer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


import com.google.gson.Gson;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.HttpMultipartMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.UUID;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    private static final int REQUEST_OPEN_IMAGE = 1337;
    private static final int REQUEST_SAVE_COLOR_IMAGE = 1338;
    private static final int REQUEST_SAVE_BW_IMAGE = 1339;

    public void hideUI() {
        findViewById(R.id.colorizer_layout).setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    public void createUI() {
        hideUI();

        if (ColorizerManager.getBitmap() != null) {
            findViewById(R.id.warning_layout).setVisibility(View.GONE);
            if (ColorizerManager.getResults() == null) {
                findViewById(R.id.preview_buttons).setVisibility(View.GONE);
            }
            findViewById(R.id.results_layout).setVisibility(View.VISIBLE);
            findViewById(R.id.options_layout).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.warning_layout).setVisibility(View.VISIBLE);
            findViewById(R.id.results_layout).setVisibility(View.GONE);
            findViewById(R.id.options_layout).setVisibility(View.GONE);

            findViewById(R.id.warning_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    findViewById(R.id.warning_layout).setVisibility(View.GONE);
                    findViewById(R.id.results_layout).setVisibility(View.VISIBLE);
                    findViewById(R.id.options_layout).setVisibility(View.VISIBLE);
                }
            });
        }

        findViewById(R.id.scan_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorizerManager.setBitmap(null);
                ColorizerManager.setResults(null);
                findViewById(R.id.preview_buttons).setVisibility(View.GONE);

                System.out.println("Scanner clicked");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Connecting...");
                        ScannerManager.connect(ScannerManager.CONNECTION.WIFI, getApplicationContext());
                        while (!ScannerManager.isConnected()) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.out.println("Still Connecting...");
                        }
                        System.out.println("Connected");

                        System.out.println("Attempting scan...");
                        ScannerManager.executeImageScan(
                                ScannerManager.createIConnector(getApplicationContext()), FullscreenActivity.this);

                        System.out.println("Waiting for data.");
                        while (ColorizerManager.getBitmap() == null)
                        {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.out.println("Still Waiting...");
                        }

                        System.out.println("Found the image.");
                        loadInitialImage();
                    }
                }).start();
            }
        });

        findViewById(R.id.load_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorizerManager.setBitmap(null);
                ColorizerManager.setResults(null);
                findViewById(R.id.preview_buttons).setVisibility(View.GONE);

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, REQUEST_OPEN_IMAGE);
            }
        });

        findViewById(R.id.load_sample).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorizerManager.setBitmap(null);
                ColorizerManager.setResults(null);
                findViewById(R.id.preview_buttons).setVisibility(View.GONE);

                // Load sample bitmap.
                Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.einstein);
                ColorizerManager.setBitmap(bm);
                loadInitialImage();
            }
        });
    }

    protected void loadInitialImage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Bitmap image = ColorizerManager.getBitmap();
                DisplayMetrics met = getApplicationContext()
                        .getResources()
                        .getDisplayMetrics();

                float ratio = (float) image.getWidth() / (float) image.getHeight();
                int height =  (int) (met.heightPixels * 0.2);
                int width = (int) (height * ratio);

                image  = Bitmap.createScaledBitmap(image, width, height, true);

                int size = 3;
                Bitmap borderImage = Bitmap.createBitmap(
                        image.getWidth() + size * 2,
                        image.getHeight() + size * 2,
                        image.getConfig());
                Canvas canvas = new Canvas(borderImage);
                canvas.drawColor(Color.BLACK);
                canvas.drawBitmap(image, size, size, null);
                image = borderImage;


                ImageView preview = new ImageView(getApplicationContext());
                preview.setImageBitmap(image);


                LinearLayout layout = findViewById(R.id.layout_pictures);
                layout.removeAllViews();
                layout.addView(preview, 0);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        startColorizer();
                    }
                }).start();
            }
        });
    }

    protected void startColorizer() {
        final TextView text = new TextView(getApplicationContext());
        text.setText("Processing (estimate: 3-5 minutes)");
        text.setGravity(Gravity.CENTER);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                LinearLayout layout = findViewById(R.id.layout_pictures);
                layout.addView(text, 1);
            }
        });

        // Convert the image to a byteArray.
        System.out.println("Converting to base64.");
        Bitmap image = ColorizerManager.getBitmap();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream .toByteArray();

        // Setup the httpClient for fetching data.
        HttpClient httpclient = new DefaultHttpClient();
        httpclient.getConnectionManager().getSchemeRegistry().register(
                new Scheme("https", SSLSocketFactory.getSocketFactory(), 443)
        );
        HttpPost httpPost = new HttpPost("https://9may.mail.ru/photo");
        httpPost.setHeader("Referer", "https://9may.mail.ru/restoration/?lang=en");
        httpPost.setHeader("Origin", "https://9may.mail.ru");
        httpPost.setHeader("Host", "9may.mail.ru");
        httpPost.setHeader("X-Requested-With", "XMLHttpRequest");
        httpPost.setHeader("Accept", "application/json, text/javascript, */*; q=0.01");
        httpPost.setHeader("Accept-Language", "en-US,en;q=0.9");
        httpPost.setHeader("Sec-Fetch-Site", "same-origin");
        httpPost.setHeader("Sec-Fetch-Mode", "cors");
        httpPost.setHeader("Sec-Fetch-Dest", "empty");
        httpPost.setHeader("Connection", "keep-alive");
        String boundary = "----WebKitFormBoundary"
                            + UUID.randomUUID().toString().substring(0, 16);
                            //+ "5gVdAgBGeAERsh6B"; // 16 Char Unique.
        httpPost.setHeader("Content-Type", "multipart/form-data; boundary="+boundary);

        ByteArrayBody imageBody = new ByteArrayBody(imageBytes, "input.png");
        HttpEntity entity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .setBoundary(boundary)
                .addPart("image[]",imageBody)
                .build();
        httpPost.setEntity(entity);

        //Make the http call.
        try {
            HttpResponse response = httpclient.execute(httpPost);
            if(response == null){ setupFailureUI(); };

            InputStream source = response.getEntity().getContent();
            Reader reader = new InputStreamReader(source);

            Gson gson = new Gson();
            PhotoResponse photo = gson.fromJson(reader, PhotoResponse.class);
            String[] results = photo.getPayload();

            if(results.length != 2){ setupFailureUI(); };

            byte[] imageString = Base64.decode(results[0], Base64.DEFAULT);
            Bitmap improved = BitmapFactory.decodeByteArray(imageString, 0, imageString.length);
            imageString = Base64.decode(results[1], Base64.DEFAULT);
            Bitmap colorized = BitmapFactory.decodeByteArray(imageString, 0, imageString.length);

            ColorizerManager.setResults(new Bitmap[] { improved, colorized });
            setupPreviewUI();
        } catch (Exception e) { // YOLO Exception Catching
            setupFailureUI();
            e.printStackTrace();
        }
    }

    protected void setupFailureUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView text = new TextView(getApplicationContext());
                text.setText("Failed to colorize.");
                text.setGravity(Gravity.CENTER);

                LinearLayout layout = findViewById(R.id.layout_pictures);
                layout.removeViewAt(1);
                layout.addView(text, 1);
            }
        });
    }

    protected void setupPreviewUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView text = new TextView(getApplicationContext());
                text.setText("Tap the buttons to see a preview.\n" +
                        "Tap the preview to save to a file.");
                text.setGravity(Gravity.CENTER);

                LinearLayout layout = findViewById(R.id.layout_pictures);
                layout.removeViewAt(1);
                layout.addView(text, 1);

                findViewById(R.id.preview_buttons).setVisibility(View.VISIBLE);
                findViewById(R.id.black_and_white_preview).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Bitmap image = ColorizerManager.getResults()[0];
                        DisplayMetrics met = getApplicationContext()
                                .getResources()
                                .getDisplayMetrics();

                        float ratio = (float) image.getWidth() / (float) image.getHeight();
                        int height =  (int) (met.heightPixels * 0.2);
                        int width = (int) (height * ratio);

                        image  = Bitmap.createScaledBitmap(image, width, height, true);

                        int size = 3;
                        Bitmap borderImage = Bitmap.createBitmap(
                                image.getWidth() + size * 2,
                                image.getHeight() + size * 2,
                                image.getConfig());
                        Canvas canvas = new Canvas(borderImage);
                        canvas.drawColor(Color.BLACK);
                        canvas.drawBitmap(image, size, size, null);
                        image = borderImage;


                        ImageView preview = new ImageView(getApplicationContext());
                        preview.setImageBitmap(image);
                        preview.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("image/png");
                                intent.putExtra(Intent.EXTRA_TITLE, "Colorizer_Fixed_Output.png");
                                startActivityForResult(intent, REQUEST_SAVE_BW_IMAGE);
                            }
                        });

                        LinearLayout layout = findViewById(R.id.layout_pictures);
                        if (layout.getChildCount() > 2) {
                            layout.removeViewAt(2); //Remove last preview
                        }
                        layout.addView(preview, 2);
                    }
                });
                findViewById(R.id.color_preview).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Bitmap image = ColorizerManager.getResults()[1];
                        DisplayMetrics met = getApplicationContext()
                                .getResources()
                                .getDisplayMetrics();

                        float ratio = (float) image.getWidth() / (float) image.getHeight();
                        int height =  (int) (met.heightPixels * 0.2);
                        int width = (int) (height * ratio);

                        image  = Bitmap.createScaledBitmap(image, width, height, true);

                        int size = 3;
                        Bitmap borderImage = Bitmap.createBitmap(
                                image.getWidth() + size * 2,
                                image.getHeight() + size * 2,
                                image.getConfig());
                        Canvas canvas = new Canvas(borderImage);
                        canvas.drawColor(Color.BLACK);
                        canvas.drawBitmap(image, size, size, null);
                        image = borderImage;


                        ImageView preview = new ImageView(getApplicationContext());
                        preview.setImageBitmap(image);
                        preview.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                intent.setType("image/png");
                                intent.putExtra(Intent.EXTRA_TITLE, "Colorizer_Color_Output.png");
                                startActivityForResult(intent, REQUEST_SAVE_COLOR_IMAGE);
                            }
                        });

                        LinearLayout layout = findViewById(R.id.layout_pictures);
                        if (layout.getChildCount() > 2) {
                            layout.removeViewAt(2); //Remove last preview
                        }
                        layout.addView(preview, 2);
                    }
                });
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        hideUI();
        createUI();
    }

    @Override
    protected void onResume() {
        super.onResume();

        hideUI();
        createUI();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_IMAGE) {
            if (resultCode == RESULT_OK) {
                Uri photoUri = data.getData();
                try {
                    Bitmap picture = MediaStore.
                            Images.Media.getBitmap(this.getContentResolver(), photoUri);
                    ColorizerManager.setBitmap(picture);
                    loadInitialImage();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == REQUEST_SAVE_BW_IMAGE || requestCode == REQUEST_SAVE_COLOR_IMAGE) {
            if (resultCode == RESULT_OK) {
                Bitmap image = null;
                if (requestCode == REQUEST_SAVE_BW_IMAGE) {
                    image = ColorizerManager.getResults()[0];
                } else if (requestCode == REQUEST_SAVE_COLOR_IMAGE) {
                    image = ColorizerManager.getResults()[1];
                }

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                byte[] bytes = stream.toByteArray();
                image.recycle();

                try {
                    OutputStream out = getContentResolver().openOutputStream(data.getData());
                    out.write(bytes);
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

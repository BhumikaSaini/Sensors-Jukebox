package in.gnuify.musicplayer;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    TextView title;
    ImageView albumArt;
    SeekBar progress;
    Button playPause;

    ListView lv;
    ArrayList<String> item;
    ArrayList<Uri> songList;
    ArrayList<Uri> albumArtList;
    ArrayAdapter<String> adapter;
    MediaPlayer mp;
    Handler mHandler;
    SensorManager sensorMgr;

    long lastUpdate;
    float last_x, last_y, last_z;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        title = findViewById(R.id.songTitle);
        albumArt = findViewById(R.id.albumArt);
        progress = findViewById(R.id.progressBar);
        playPause = findViewById(R.id.play);
        mHandler=new Handler();
        item=new ArrayList<String>();
        songList=new ArrayList<Uri>();
        albumArtList=new ArrayList<Uri>();
        //to get permissions at runtime for Marshmallow+
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        if(permissionCheck== PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
        }
        else
            fetchSongs();

        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorMgr.registerListener(this, sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
        sensorMgr.registerListener(this, sensorMgr.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_GAME);

        playPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mp==null){
                    playSong();
                    //isPlaying=true;

                }
                else if(mp.isPlaying()) {
                    mp.pause();
                }
                else {
                    mp.seekTo(mp.getCurrentPosition());
                    mp.start();
                }

            }
        });
    }

    private void playSong() {
        if(mp!=null) {
            if (mp.isPlaying())
                mp.release();
        }
        else {
            title.setText(item.get(0));
            mp = MediaPlayer.create(MainActivity.this, songList.get(0));

            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        getContentResolver(), albumArtList.get(0));
                bitmap = Bitmap.createScaledBitmap(bitmap, 30, 30, true);
                albumArt.setImageBitmap(bitmap);
            } catch (Exception e) {
                e.printStackTrace();
            }

            progress.setMax(mp.getDuration());
            mp.start();
            mHandler.post(th);
        }
    }


    void fetchSongs() {

        ContentResolver cr = getContentResolver();
        ArrayList<Uri>songs=new ArrayList<Uri>();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
        Cursor cur = cr.query(uri, null, selection, null, sortOrder);
        int count = 0;
        if(cur==null){
            Toast.makeText(this,"Invalid Error",Toast.LENGTH_SHORT).show();
        }
        else if(!cur.moveToFirst()){
            Toast.makeText(this,"No song found",Toast.LENGTH_SHORT).show();
        }

        else
        {
            count = cur.getCount();

            //int albumArt=(cur.getColumnIndex(MediaStore.Audio.Media.ALBUM));
            //String path = cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
            int titleColumn=cur.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int idColumn=cur.getColumnIndex(MediaStore.Audio.Media._ID);
            int albumIdColumn=cur.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            Uri sArtworkUri = Uri
                    .parse("content://media/external/audio/albumart");
            do{
                long id=cur.getLong(idColumn);
                String title=cur.getString(titleColumn);
                long albumId=cur.getLong(albumIdColumn);
                Uri songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id);

                Uri albumArtUri = ContentUris.withAppendedId(sArtworkUri, albumId);
                songList.add(songUri);
                item.add(title);
                albumArtList.add(albumArtUri);
            }while (cur.moveToNext());

        }

        cur.close();

    }

    Runnable th=new Runnable() {
        @Override
        public void run() {
            progress.setProgress(mp.getCurrentPosition());
            mHandler.postDelayed(th,1000);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode){
            case 1:
                if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED){
                    Toast.makeText(this,"granted",Toast.LENGTH_LONG).show();
                    fetchSongs();
                    adapter.notifyDataSetChanged();
                }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (mp != null && sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && false) {
            long curTime = System.currentTimeMillis();
            // only allow one update every 100ms.
            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;

                float x = sensorEvent.values[SensorManager.DATA_X];
                float y = sensorEvent.values[SensorManager.DATA_Y];
                float z = sensorEvent.values[SensorManager.DATA_Z];

                float speed = Math.abs(x+y+z - last_x - last_y - last_z) / diffTime * 10000;

                if (speed > 800) {
                    Log.d("sensor", "shake detected w/ speed: " + speed);
                    Toast.makeText(this, "shake detected w/ speed: " + speed, Toast.LENGTH_SHORT).show();
                }
                last_x = x;
                last_y = y;
                last_z = z;
            }
        } else if (mp != null && sensorEvent.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = sensorEvent.values[0];
            if(lux == 0 && mp.isPlaying() ) {
                mp.pause();
            } else if (lux > 4) {
                mp.start();
            }


            Log.d("Light", lux + "");
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

}

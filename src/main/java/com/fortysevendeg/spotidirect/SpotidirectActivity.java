package com.fortysevendeg.spotidirect;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.widget.TextView;

import java.io.IOException;
import java.nio.charset.Charset;

public class SpotidirectActivity extends Activity {

    private NfcAdapter nfcAdapter;

    private static final String APP_MIME_TYPE = "application/com.fortysevendeg.spotidirect";

    private String keySpotify = null;

    private TextView message;

    private TextView messageNFC;

    private boolean writeMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        String url = null;

        if ("text/plain".equals(getIntent().getType())) {
            url = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        }

        if (!TextUtils.isEmpty(getIntent().getDataString())) {
            url = getIntent().getDataString();
        }

        if (!TextUtils.isEmpty(url) && url.startsWith("http://open.spotify.com/")) {
            keySpotify = url.replace("http://open.spotify.com/", "");
        }

        message = (TextView) findViewById(R.id.message);

        messageNFC = (TextView) findViewById(R.id.messageNFC);

        if (keySpotify != null) {
            message.setText(getString(R.string.messageNFC, getTypeKey(keySpotify)));
            // NFC init
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
            if (nfcAdapter == null) {
                message.setText(R.string.noNFC);
            }
        } else {
            message.setText(R.string.noKeySpotify);
        }

    }

    private void enableWriteMode() {
        writeMode = true;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter[] filters = new IntentFilter[]{tagDetected};
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, null);
    }

    private void disableWriteMode() {
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(keySpotify) && nfcAdapter != null) {
            enableWriteMode();
        }
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            ndefDiscovered(getIntent());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableWriteMode();
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (writeMode) {
            writeMode = false;
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            writeTag(tag);
        }
    }

    private boolean writeTag(Tag tag) {
//        NdefRecord appRecord = NdefRecord.createApplicationRecord(getPackageName());

        byte[] mimeBytes = APP_MIME_TYPE.getBytes(Charset.forName("US-ASCII"));

        NdefRecord cardRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA, mimeBytes, new byte[0], keySpotify.getBytes());
        NdefMessage message = new NdefMessage(new NdefRecord[]{cardRecord});

        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    messageNFC.setText(R.string.readOnlyTag);
                    return false;
                }
                int length = message.toByteArray().length;
                if (ndef.getMaxSize() < length) {
                    messageNFC.setText(R.string.notFreeSpace);
                    return false;
                }
                ndef.writeNdefMessage(message);
                messageNFC.setText(R.string.tagWritten);
                return true;
            } else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        messageNFC.setText(R.string.tagWritten);
                        return true;
                    } catch (IOException e) {
                        messageNFC.setText(R.string.unableFormatNDEF);
                        return false;
                    }
                } else {
                    messageNFC.setText(R.string.notSupportNDEFFormat);
                    return false;
                }
            }
        } catch (Exception e) {
            messageNFC.setText(R.string.failedWriteTag);
        }
        return false;
    }

    private void ndefDiscovered(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        String payload = new String(msg.getRecords()[0].getPayload());
        String uri = "spotify:" + payload;
        Intent launcher = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
        startActivity(launcher);
    }

    private String getTypeKey(String key) {
        if (key.startsWith("user")) {
            return getString(R.string.keyTypeList);
        } else if (key.startsWith("track")) {
            return getString(R.string.keyTypeTrack);
        } else if (key.startsWith("album")) {
            return getString(R.string.keyTypeAlbum);
        }
        return getString(R.string.keyTypeUnknown);
    }

}


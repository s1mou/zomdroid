package com.zomdroid.fragments;

import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.zomdroid.R;
import java.io.File;

public class WorkshopFragment extends Fragment {
    private EditText inputWorkshopUrl;
    private Button downloadButton;
    private TextView outputText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_workshop, container, false);
        inputWorkshopUrl = view.findViewById(R.id.input_workshop_url);
        downloadButton = view.findViewById(R.id.button_download_mod);
        outputText = view.findViewById(R.id.text_output);

        downloadButton.setOnClickListener(v -> {
            String url = inputWorkshopUrl.getText().toString().trim();
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getContext(), "Please enter a Workshop link", Toast.LENGTH_SHORT).show();
                return;
            }
            // Extract Workshop file ID from URL
            String fileId = extractWorkshopFileId(url);
            if (fileId == null) {
                outputText.setText("Invalid Workshop link. Could not extract file ID.");
                return;
            }
            // Prepare SteamCMD command for preview
            String steamCmdPath = "/data/data/your.package.name/files/box64 /data/data/your.package.name/files/steamcmd.sh";
            String downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            String command = steamCmdPath +
                    " +login anonymous" +
                    " +workshop_download_item 108600 " + fileId +
                    " +quit" +
                    " (Download dir: " + downloadDir + ")";
            outputText.setText(
                "Extracted Workshop file ID: " + fileId +
                "\n\nSteamCMD command preview:\n" + command
            );
        });
        return view;
    }

    /**
     * Extracts the Workshop file ID from a Steam Workshop URL.
     * Supports URLs like https://steamcommunity.com/sharedfiles/filedetails/?id=123456789
     */
    private String extractWorkshopFileId(String url) {
        String idPattern = "[?&]id=(\\d+)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(idPattern);
        java.util.regex.Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}

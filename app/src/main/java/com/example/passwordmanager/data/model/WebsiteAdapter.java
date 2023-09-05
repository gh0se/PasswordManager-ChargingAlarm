package com.example.passwordmanager.data.model;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passwordmanager.R;
import com.example.passwordmanager.data.model.databae.AppDatabase;
import com.google.gson.Gson;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebsiteAdapter extends RecyclerView.Adapter<WebsiteAdapter.WebsiteViewHolder> {

    public static List<Website> websites; // Your data source
    private OnItemClickListener listener;
    private Context mContext;
    private RecyclerView mRecyclerView;

    public List<Website> allWebsites;  // This will store all websites
    public List<Website> filteredWebsites; // This will store filtered websites


    public WebsiteAdapter(Context context, List<Website> websites, OnItemClickListener listener, RecyclerView recyclerView) {
        this.mContext = context;
        this.allWebsites = (websites != null) ? new ArrayList<>(websites) : new ArrayList<>();
        this.filteredWebsites = new ArrayList<>(allWebsites);
        this.listener = listener;
        this.mRecyclerView = recyclerView;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filter(String query) {

        if(allWebsites != null) {
            filteredWebsites.clear();
            if (query.isEmpty()) {
                filteredWebsites.addAll(allWebsites);
            } else {
                for (Website website : allWebsites) {
                    if (website.getWebsiteName() != null && website.getWebsiteName().toLowerCase().contains(query.toLowerCase())) {
                        filteredWebsites.add(website);
                        Log.d("FilterMethod", "Filter called with query: " + query);
                    }
                }
            }
            notifyDataSetChanged();
            Log.d("FilterDebug", "Number of items after filter: " + filteredWebsites.size());
        }
    }




    @NonNull
    @Override
    public WebsiteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_website, parent, false);
        return new WebsiteViewHolder(view);
    }

    public void setWebsites(List<Website> websites) {
        this.allWebsites = (websites != null) ? new ArrayList<>(websites) : new ArrayList<>();
        this.filteredWebsites = new ArrayList<>(allWebsites);
        notifyDataSetChanged();
    }


    @Override
    public void onBindViewHolder(@NonNull WebsiteViewHolder holder, int position) {
        Website website = filteredWebsites.get(position);
        holder.websiteName.setText(website.getWebsiteName());
        holder.email.setText(website.getEmail());
        holder.password.setText(website.getPassword());

        holder.editButton.setOnClickListener(v -> editItem(website));
        holder.deleteButton.setOnLongClickListener(v -> {
            Log.d("DEBUG", "Delete button long pressed!");
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                showDeleteConfirmationDialog(pos, filteredWebsites.get(pos));
            }
            return true;
        });

    }

    private void editItem(Website website) {
        // Inflate the custom layout/view
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View editView = inflater.inflate(R.layout.dialog_edit_website, null);

        // Reference the input fields and populate with existing data
        EditText websiteNameInput = editView.findViewById(R.id.website_name_input);
        EditText emailInput = editView.findViewById(R.id.email_input);
        EditText passwordInput = editView.findViewById(R.id.password_input);

        websiteNameInput.setText(website.getWebsiteName());
        emailInput.setText(website.getEmail());
        passwordInput.setText(website.getPassword());

        // Inflate custom title view
        View customTitleView = inflater.inflate(R.layout.custom_dialog_title, null);
        TextView customTitleTextView = customTitleView.findViewById(R.id.customDialogTitle);
        customTitleTextView.setText("Edit Website");

        // Create the alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.MyAlertDialogTheme);
        builder.setCustomTitle(customTitleView)
                .setView(editView)
                .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Update the website object with new data
                        website.setWebsiteName(websiteNameInput.getText().toString());
                        website.setEmail(emailInput.getText().toString());
                        website.setPassword(passwordInput.getText().toString());

                        // Update database in a background thread
                        AsyncTask.execute(() -> {
                            AppDatabase.getDatabase(mContext).websiteDao().update(website);

                            // Fetch updated data
                            List<Website> updatedWebsites = AppDatabase.getDatabase(mContext).websiteDao().getAllWebsites("%");

                            // Update UI in the main thread
                            ((Activity) mContext).runOnUiThread(() -> {
                                setWebsites(updatedWebsites);
                            });
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



    private void deleteItem(int position, Website website) {
        // Remove from both lists
        allWebsites.remove(website);  // Add this line
        filteredWebsites.remove(position);
        notifyItemRemoved(position);
        AsyncTask.execute(() -> AppDatabase.getDatabase(mContext).websiteDao().delete(website));
    }






    private void showDeleteConfirmationDialog(final int position, final Website website) {
        LayoutInflater inflater = LayoutInflater.from(mContext);

        // Inflate custom title view
        View customTitleView = inflater.inflate(R.layout.custom_dialog_title, null);
        TextView customTitleTextView = customTitleView.findViewById(R.id.customDialogTitle);
        customTitleTextView.setText("Confirm Delete");

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext, R.style.MyAlertDialogTheme);
        builder.setCustomTitle(customTitleView)
                .setMessage("Are you sure you want delete login for website: " + website.getWebsiteName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> deleteItem(position, website))
                .setNegativeButton("No", null)
                .show();
    }




    @Override
    public int getItemCount() {
        return filteredWebsites.size();
    }

    public void updateData(List<Website> newWebsites) {
        this.websites = newWebsites;
        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onItemClick(Website website);
    }



        public class WebsiteViewHolder extends RecyclerView.ViewHolder {
            TextView websiteName, email, password;
            ImageButton editButton, deleteButton;

            public WebsiteViewHolder(@NonNull View itemView) {
                super(itemView);
                websiteName = itemView.findViewById(R.id.website_name);
                email = itemView.findViewById(R.id.email);
                password = itemView.findViewById(R.id.password);
                editButton = itemView.findViewById(R.id.edit_button);
                deleteButton = itemView.findViewById(R.id.delete_button);

                deleteButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Log.d("DEBUG", "Delete button long pressed!");
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            Website website = filteredWebsites.get(position);
                            showDeleteConfirmationDialog(position, website);
                        }
                        return true;
                    }
                });

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (password.getInputType() == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                            password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        } else {
                            password.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        }
                    }
                });
            }





        }
}


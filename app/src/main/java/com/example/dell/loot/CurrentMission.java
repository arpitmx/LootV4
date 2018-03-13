package com.example.dell.loot;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class CurrentMission
        extends
        Fragment
        implements
        LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, ResultCallback<LocationSettingsResult> {

    static final int LOCATION_REQUEST_CODE = 1;
    static final int REQUEST_CHECK_SETTINGS = 100;
    static final int STATE_LOCATE = 0, STATE_SOLVE = 1;
    Button submit, drop_mission;
    TextView story, question;
    EditText answer;
    AlertDialog alertDialog;
    Mission mission;
    int userStage, state, dropCount, score;
    String userID;
    Vibrator vibrator;
    GoogleApiClient googleApiClient;
    LocationRequest locationRequest;
    FirebaseDatabase database;
    DatabaseReference users, missions;
    ProgressDialog progressDialog;
    LootApplication app;
    RequestQueue requestQueue;
    SharedPreferences sharedPreferences;

    public CurrentMission() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_current_mission, container, false);
        story = view.findViewById(R.id.story);
        question = view.findViewById(R.id.question);
        answer = view.findViewById(R.id.answer);
        submit = view.findViewById(R.id.submit);
        drop_mission = view.findViewById(R.id.drop_mission);
        vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        sharedPreferences = getActivity().getSharedPreferences("LootPrefs", Context.MODE_PRIVATE);
        userStage = sharedPreferences.getInt("com.hackncs.stage",1);
        state = sharedPreferences.getInt("com.hackncs.state", 0);
        dropCount = sharedPreferences.getInt("com.hackncs.dropCount", 0);
        score = sharedPreferences.getInt("com.hackncs.score", 0);
        userID = sharedPreferences.getString("com.hackncs.userID", null);
        requestQueue = Volley.newRequestQueue(getContext());
        checkPermission();
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        progressDialog = new ProgressDialog(getContext());
        progressDialog.setMessage("Loading...");
        progressDialog.show();
        googleApiClient = new GoogleApiClient.Builder(getContext())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        StringRequest fetchMission = new StringRequest(Request.Method.GET,
                Endpoints.fetchMission + userStage,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        JSONObject jsonObject;
                        mission = new Mission();
                        try {
                            jsonObject = new JSONObject(response);
                            mission.setMissionID(jsonObject.getInt("id"));
                            mission.setMissionName(jsonObject.getString("mission_name"));
                            mission.setStory(jsonObject.getString("story"));
                            mission.setDescription(jsonObject.getString("description"));
                            String g = jsonObject.getString("geocode");
                            mission.setLat(Double.valueOf(g.substring(0, g.indexOf(" "))));
                            mission.setLng(Double.valueOf(g.substring(g.indexOf(" "))));
                            mission.setAnswer(jsonObject.getString("answer"));
                            displayMission();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        progressDialog.dismiss();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        progressDialog.dismiss();
                        Toast.makeText(getContext(), "Error in fetching data!", Toast.LENGTH_SHORT).show();
                    }
                });
        requestQueue.add(fetchMission);
        drop_mission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setTitle("Drop Mission");
                if (score - (((int)Math.pow(2, dropCount))*10) >= 0) {
                    builder.setMessage((((int) Math.pow(2, dropCount)) * 10) + " coins will be deducted!");
                    builder.setPositiveButton("Drop", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            userStage += 1;
                            state = 0;
                            score -= ((int) Math.pow(2, dropCount)) * 10;
                            dropCount += 1;
                            StringRequest updateUser = new StringRequest(Request.Method.POST,
                                    Endpoints.updateUser + userID +"/edit/",
                                    new Response.Listener<String>() {
                                        @Override
                                        public void onResponse(String response) {
                                            Log.d("dropVolley", "response");
                                            SharedPreferences.Editor editor = sharedPreferences.edit();
                                            editor.putInt("com.hackncs.score", score);
                                            editor.putInt("com.hackncs.stage", userStage);
                                            editor.putInt("com.hackncs.state", state);
                                            editor.putInt("com.hackncs.dropCount", dropCount);
                                            editor.apply();
                                        }
                                    },
                                    new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            Log.d("dropVolley", "error");
                                        }
                                    }){
                                @Override
                                protected Map<String, String> getParams() throws AuthFailureError {
                                    Map map = new HashMap();
                                    map.put("score", score);
                                    map.put("stage", userStage);
                                    map.put("mission_state", "false");
                                    map.put("drop_count", dropCount);
                                    return super.getParams();
                                }
                            };
                            requestQueue.add(updateUser);
                            getFragmentManager().beginTransaction().detach(CurrentMission.this).attach(CurrentMission.this).commit();
                        }
                    });
                    builder.setNegativeButton("Stay", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            alertDialog.dismiss();
                        }
                    });
                } else {
                    builder.setMessage("You can't drop this mission! Not enough coins!");
                    builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            alertDialog.dismiss();
                        }
                    });
                    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            alertDialog.dismiss();
                        }
                    });
                }
                alertDialog = builder.create();
            }
        });
//        database = FirebaseDatabase.getInstance();
//        users = database.getReference("Users");
//        missions = database.getReference("Missions");
//        progressDialog = new ProgressDialog(getContext());
//        app = (LootApplication)getActivity().getApplication();
//        if(app.user.active != null) {
//            progressDialog.setMessage("Loading Mission...");
//            progressDialog.setTitle("Please Wait...");
//            progressDialog.show();
//            missions.child(app.user.active).addValueEventListener(new ValueEventListener() {
//                @Override
//                public void onDataChange(DataSnapshot dataSnapshot) {
//                    mission = dataSnapshot.getValue(Mission.class);
//                    story.setText(mission.getStory());
//                    question.setText(mission.getDescription());
//                    if(progressDialog.isShowing()) {
//                        progressDialog.dismiss();
//                    }
//                }
//
//                @Override
//                public void onCancelled(DatabaseError databaseError) {
//
//                }
//            });
//        }
//        submit.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                if(answer.getText().toString().equalsIgnoreCase(mission.getAnswer())) {
//                    app.user.active = null;
//                    app.user.completed.add(mission.getMissionID());
//                    app.user.score += 10;
//                    users.child(app.user.getUserID()).setValue(app.user);
//                }
//            }
//        });
//
//        drop_mission.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                    app.user.active = null;
//                    app.user.dropped.add(mission.getMissionID());
//                    app.user.score -= 2;
//                    users.child(app.user.getUserID()).setValue(app.user);
//            }
//        }); {
    }

    private void displayMission() {
        switch (state) {
            case STATE_LOCATE:
                checkPermission();
                if (!googleApiClient.isConnected()) {
                    googleApiClient.connect();
                }
                question.setText(mission.getStory());
                answer.setEnabled(false);
                submit.setEnabled(false);
                break;
            case STATE_SOLVE:
                question.setText(mission.getDescription());
                answer.setEnabled(true);
                submit.setEnabled(true);
                submit.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!answer.getText().toString().equals(mission.getAnswer())) {
                            answer.setText("");
                            Toast.makeText(getContext(), "You might wanna try another one!", Toast.LENGTH_SHORT).show();
                        } else {
                            answer.setText("");
                            userStage += 1;
                            state = 0;
                            score += 100;
                            StringRequest updateUser = new StringRequest(Request.Method.POST,
                                    Endpoints.updateUser + userID +"/edit/",
                                    new Response.Listener<String>() {
                                        @Override
                                        public void onResponse(String response) {
                                            Log.d("submitVolley", "response");
                                            SharedPreferences.Editor editor = sharedPreferences.edit();
                                            editor.putInt("com.hackncs.score", score);
                                            editor.putInt("com.hackncs.stage", userStage);
                                            editor.putInt("com.hackncs.state", state);
                                            editor.apply();
                                            getFragmentManager().beginTransaction().detach(CurrentMission.this).attach(CurrentMission.this).commit();
                                        }
                                    },
                                    new Response.ErrorListener() {
                                        @Override
                                        public void onErrorResponse(VolleyError error) {
                                            Log.d("submitVolley", error.getMessage());
                                        }
                                    }) {
                                @Override
                                protected Map<String, String> getParams() throws AuthFailureError {
                                    Map map = new HashMap();
                                    map.put("score", String.valueOf(score));
                                    map.put("stage", String.valueOf(userStage));
                                    map.put("mission_state", "false");
                                    return map;
                                }
                            };
                            requestQueue.add(updateUser);
                        }
                    }
                });
                break;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(1000);
        checkPermission();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.
                checkLocationSettings(googleApiClient, builder.build());
        result.setResultCallback(this);


        LocationServices.FusedLocationApi.
                requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Toast.makeText(getContext(), "Connection Suspended!", Toast.LENGTH_SHORT).show();
        checkPermission();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(getContext(), "Connection Failed!", Toast.LENGTH_SHORT).show();
        checkPermission();
    }

    @Override
    public void onLocationChanged(Location location) {
        Location missionLocation = new Location("");
        missionLocation.setLatitude(mission.getLat());
        missionLocation.setLongitude(mission.getLng());
//        Toast.makeText(getContext(), location.distanceTo(missionLocation)+"", Toast.LENGTH_SHORT).show();
        if (location.distanceTo(missionLocation) < 5) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (googleApiClient.isConnected()) {
                    googleApiClient.disconnect();
                }
//                vibrator.vibrate(VibrationEffect.createWaveform(
//                        new long[]{0, 250, 200, 250, 150, 150, 75, 150, 75, 150}, -1));
                vibrator.vibrate(3000);
            }
            state = 1;
            StringRequest updateUser = new StringRequest(Request.Method.POST,
                    Endpoints.updateUser + userID +"/edit/",
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.d("updateVolley", "response");
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putInt("com.hackncs.state", state);
                            editor.apply();
                            getFragmentManager().beginTransaction().detach(CurrentMission.this).attach(CurrentMission.this).commit();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Log.d("updateVolley", error.getMessage());
                        }
                    }){
                @Override
                protected Map<String, String> getParams() throws AuthFailureError {
                    Map map = new HashMap();
                    map.put("mission_state", "true");
                    return map;
                }
            };
            requestQueue.add(updateUser);
        }
    }

    private void checkPermission() {
        if (ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (!(grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(getContext(), "Permission not granted!", Toast.LENGTH_SHORT).show();
            }
            else
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    new AlertDialog.Builder(getContext())
                            .setMessage("The app needs to access device's location to function properly!")
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    checkPermission();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .create()
                            .show();
                }
            }
        }
    }

    @Override
    public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
        Status status = locationSettingsResult.getStatus();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                // NO need to show the dialog;
                break;
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                try {
                    status.startResolutionForResult(getActivity(), REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                // Location settings are unavailable so not possible to show any dialog now
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    @Override
    public void onStop() {
        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        super.onStop();
    }
    //    @Override
//    public void onDestroyView() {
//        Log.i("Current Mission","Destroy View");
//        if(progressDialog.isShowing()) {
//            progressDialog.dismiss();
//        }
//        super.onDestroyView();
//    }
//
//    @Override
//    public void onStart() {
//          if(app.user.getActive()==null) {
//            showDialog();
//          }
//          super.onStart();
//    }
//
//    @Override
//    public void onStop() {
//        Log.i("Current Mission","Stop");
//        super.onStop();
//    }
//
//    private void showDialog() {
//        AlertDialog alertDialog = new AlertDialog.Builder(getActivity()).create();
//        alertDialog.setTitle("Alert");
//        alertDialog.setMessage("No Active Mission");
//        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
//            public void onClick(DialogInterface dialog, int which) {
//                BottomNavigationView navigationView = getActivity().findViewById(R.id.navigation);
//                navigationView.getMenu().getItem(0).setChecked(true);
//                android.support.v4.app.Fragment fragment = new Locator();
//                FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
//                transaction.replace(R.id.frame_container, fragment,"locator");
//                transaction.addToBackStack(null);
//                transaction.commit();
//            }
//        });
//        alertDialog.show();
//    }
}

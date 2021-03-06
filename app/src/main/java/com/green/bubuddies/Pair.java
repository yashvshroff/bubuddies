package com.green.bubuddies;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

/**
 * This is the Pair activity/class that matches users with other users based on their shared classes
 *
 * As easy as 1,2,3 explanation of pairing
 * 0) initialize all of the references to the views, variables, etc. and set onClickListener for the next button
 *                                          -> the next button adds the current pair_user to the deniedMates list.
 * 1) onCreate calls findPair() -> populate classList with all of the classes that the user is taking
 *                                 populate deniedMates and msgMates with IDs of users that are already messaging the current user or are blocked
 *
 * 2) findPair() calls findPair2() -> populate potentialMates with IDs of all other users that share atleast one class with
 *                                      the current user and aren't in the list denied mates
 *                                    if potentialMates is an empty list continue to step 2.5 else choose the first User
 *                                      from potential mates as pair_user and call updateGUI
 *
 * 2.5) findPair2 calls findPair3() -> findPair3 is called if the user has already denied all the other users that they share a class with,
 *                                      in this case potentialMates is populated with all other users that are not in msgMates
 *                                      (i.e. all users that the current user has not previously messaged)
 *                                      sets the first User in potentialMates as pair_user and calls updateGUI
 *
 * 3) findPair2 or 3 calls updateGUI -> Retrieves the pair_user's info from firebase and updates the views appropriately
 *
 * Question: Why are there 3 findPair methods? why not just one findPair method?
 *  Answer: Firebase calls will not be processed one at a time but rather through listeners that will be working in the background
 *          since we need certain information from firebase to accomplish later queries/retrieval of information the findPair must call
 *          findPair2 at the end of the onDataChanged listener and similarly if needed findPair2 must call findPair3
 *
 */


public class Pair extends AppCompatActivity implements BottomMenu.BtmMenuActivity, NewMsg.newMsgActivity {

    //references to views
    private TextView txt_name;
    private TextView txt_bio;
    private TextView txt_classes;
    private ImageView img_pfp;
    private Button btn_next;

    //UID variables
    private String curr_user;
    private String pair_user;
    private ArrayList<String> potentialMates;
    private ArrayList<String> deniedMates;
    private ArrayList<String> msgMates;

    //Current User info
    private ArrayList<String> classList;
    private String gradYear;

    //Firebase values
    final FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference ref = database.getReference("Profiles");
    FirebaseAuth fAuth = FirebaseAuth.getInstance();
    FirebaseUser currentUser = fAuth.getCurrentUser();


    /**
     * This is step 0 as detailed at the start of the file.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pair);

        //the following if statements contain hard coded curr_user that will never be reached
        Bundle extras = getIntent().getExtras();
        if(extras!= null) {
            curr_user = extras.getString("UID");
        } else {
            curr_user = "2ax0y5TP9gRs9JV31d3JKSRjNz52"; //hard coded user for testing
        }

        UserDetails.uid = curr_user;

        //initialize references to views
        txt_name = findViewById(R.id.txt_name);
        txt_name.setClipToOutline(true);
        txt_bio = findViewById(R.id.txt_bio);
        txt_bio.setClipToOutline(true);
        txt_classes = findViewById(R.id.txt_classes);
        txt_classes.setClipToOutline(true);
        img_pfp = findViewById(R.id.img_pfp);
        btn_next = findViewById(R.id.btn_next);
        btn_next.setClickable(false);

        //initialize array lists used in the matching process
        classList = new ArrayList<String>();
        potentialMates = new ArrayList<String>();
        msgMates = new ArrayList<String>();
        deniedMates = new ArrayList<String>();
        findPair();

        btn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btn_next.setClickable(false);
                deniedMates.add(pair_user);
                potentialMates.remove(0);
                if (potentialMates.size() < 1){
                    findPair();
                } else {
                    pair_user = potentialMates.get(0);
                    updateGUI(potentialMates.get(0));
                }
            }
        });



    }

    /**
     * This is step 1 as detailed at the start of the file.
     */
    public void findPair() {
        txt_bio.setText("finding pair");
        //find the curr_user's grad year and populate classList for future use.
        ref.child(curr_user).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                gradYear = snapshot.child("graduationYear").getValue(String.class);
                for (DataSnapshot child: snapshot.child("classes").getChildren()){
                    String data = child.getKey();
                    classList.add(data);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
        //find the curr_user's contacts and add them to deniedMates and msgMates
        //once done processing, calls findPair2
        database.getReference("Users").child(curr_user).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.hasChild("BlockedBy")) {
                    for (DataSnapshot child : snapshot.child("BlockedBy").getChildren()) {
                        deniedMates.add(child.getValue(String.class));
                        msgMates.add(child.getValue(String.class));
                    }
                }
                for (DataSnapshot child : snapshot.child("Contacts").getChildren()) {
                    deniedMates.add(child.getValue(String.class));
                    msgMates.add(child.getValue(String.class));
                }
                findPair2();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }

        });
    }

    /**
     * This is step 2 as detailed at the start of the file.
     */
    public void findPair2() {
        //populates potentialMates with other users that they aren't currently messaging and share classes with
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String search_user = child.child("uid").getValue(String.class);
                    if ((!search_user.equals(curr_user)) && (!deniedMates.contains(search_user))) {
                        //Toast.makeText(Pair.this, "valid uid", Toast.LENGTH_LONG);
                        Iterator<DataSnapshot> courses = child.child("classes").getChildren().iterator();
                        while(courses.hasNext()){
                            //Toast.makeText(Pair.this,"searching through users", Toast.LENGTH_LONG).show();
                            if (classList.contains(courses.next().getKey())) {
                                potentialMates.add(search_user);
                                break;
                            }
                        }
                    }
                }
                if(potentialMates.size() == 0) {
                    //in the event that the user has denied or already messaged all other users
                    //that they share a class with
                    findPair3();
                } else  {
                    pair_user = potentialMates.get(0);
                    updateGUI(pair_user);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    /**
     * This is step 2.5 as detailed at the start of the file.
     */
    public void findPair3(){
        Toast.makeText(Pair.this, "No other users share a class with you, showing all users.", Toast.LENGTH_SHORT).show();
        //populate potential mates with all users that the curr_user has not messaged including
        //users that they do not share a class with
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String search_user = child.child("uid").getValue(String.class);
                    if (!search_user.equals(curr_user) && !msgMates.contains(search_user)) {
                        potentialMates.add(search_user);
                    }
                }
                pair_user = potentialMates.get(0);
                updateGUI(pair_user);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    /**
     * This is step 3 as detailed at the start of the file.
     */
    public void updateGUI(String pair_uid){
        //get all the info we need and update the views with the correct info for pair_user/pair_uid <- both vars are the same
        ref.child(pair_uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                DatabaseReference username = FirebaseDatabase.getInstance().getReference("Users").child(snapshot.child("uid").getValue().toString());
                username.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        txt_name.setText(dataSnapshot.child("username").getValue().toString());
                    }
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.w("TESTING", "onCancelled", databaseError.toException());
                    }
                });

                txt_bio.setText("About Me: " + snapshot.child("aboutMe").getValue(String.class) +
                        "\n\nMajor: " + snapshot.child("major").getValue(String.class) +
                        "\n\nGraduation Year: " + snapshot.child("graduationYear").getValue(String.class)
                    );
                btn_next.setClickable(true);
                String img = snapshot.child("picture").getValue(String.class);
                Picasso.with(Pair.this).load(img).transform(new CircleTransform()).into(img_pfp);
                String classes = "Current BU Courses: ";
                Iterator<DataSnapshot> courses = snapshot.child("classes").getChildren().iterator();
                while(courses.hasNext()){
                    classes += courses.next().getKey();
                    if (courses.hasNext())
                    classes += ", ";
                }
                txt_classes.setText(classes);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    //method for btm menu fragment implementation see BottomMenu.java for more info
    @Override
    public void updateClickableButtons(){
        BottomMenu fragment = (BottomMenu) getSupportFragmentManager().findFragmentById(R.id.btmFragmentPair);
        fragment.disableClick(BottomMenu.PAIR);
    }

    //method for btm menu fragment implementation see BottomMenu.java for more info
    @Override
    public void changeActivity(int nextAct) {

        //switch cases to correct activity in final implementation.
        switch(nextAct) {
            case (BottomMenu.PROFILE):
                Intent i = new Intent(Pair.this, MainActivity.class);
                i.putExtra("UID", curr_user);
                startActivity(i);
                overridePendingTransition(R.anim.abc_fade_in,R.anim.abc_fade_out);
                break;
            case (BottomMenu.PAIR):
                i = new Intent(Pair.this, Pair.class);
                i.putExtra("UID", curr_user);
                startActivity(i);
                overridePendingTransition(R.anim.abc_fade_in,R.anim.abc_fade_out);
                break;
            case (BottomMenu.MESSAGE):
                i = new Intent(Pair.this, Users.class);
                i.putExtra("UID",curr_user);
                startActivity(i);
                overridePendingTransition(R.anim.abc_fade_in,R.anim.abc_fade_out);
                break;
            case (BottomMenu.STORE):
                i = new Intent(Pair.this, StoreActivity.class);
                i.putExtra("UID", curr_user);
                startActivity(i);
                overridePendingTransition(R.anim.abc_fade_in,R.anim.abc_fade_out);
                break;
        }
    }

    //method for new message fragment implementation see NewMsg.java for more info
    @Override
    public void newConversation(){
        //code here to add user as a friend
        Intent i = new Intent(Pair.this,Users.class);
        i.putExtra("UID",curr_user);
        i.putExtra("chatwithid",pair_user);
        i.putExtra("from","pair");
        Log.e("Passing uid",curr_user);
        Log.e("Passing chatwithid", pair_user);
        startActivity(i); // change to messaging tab.
    }

}
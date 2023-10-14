package np.com.bryant.myapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

import np.com.bryant.myapp.model.UserModel
import np.com.bryant.myapp.databinding.ActivityMyUserBinding
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class MyUserActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyUserBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseFirestore: FirebaseFirestore
    private lateinit var currentUser: UserModel
    private lateinit var mAdView: AdView
    private lateinit var selectedImageUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        firebaseFirestore = FirebaseFirestore.getInstance()

        loadUserData()

        val imageView = findViewById<ImageView>(R.id.imageView3)
        val changeProfilePicText = findViewById<TextView>(R.id.changeProfilePicText)
        val logoutButton = findViewById<Button>(R.id.button)

        imageView.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        changeProfilePicText.setOnClickListener {
            openImagePicker()
        }

        logoutButton.setOnClickListener {
            firebaseAuth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        resultLauncher.launch(intent)
    }

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            data?.data?.let { uri ->
                selectedImageUri = uri
                uploadProfilePicture(selectedImageUri)
            }
        }
    }

    private fun uploadProfilePicture(uri: Uri) {
        // Define a path in Firebase Storage to store the profile picture
        val storageReference = FirebaseStorage.getInstance().reference.child("profile_pictures")
        val userId = firebaseAuth.currentUser?.uid ?: return
        val imageRef = storageReference.child("$userId.jpg")

        imageRef.putFile(uri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.metadata?.reference?.downloadUrl?.addOnSuccessListener { downloadUri ->
                    val imageUrl = downloadUri.toString()
                    updateProfilePicture(imageUrl)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error uploading profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfilePicture(imageUrl: String) {
        currentUser.profilePic = imageUrl

        // Save the updated user data back to Firestore
        val userId = firebaseAuth.currentUser?.uid ?: return
        firebaseFirestore.collection("users")
            .document(userId)
            .set(currentUser)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()
                loadUserData() // Reload and display the new profile picture using Glide
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating profile picture: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadUserData() {
        val userId = firebaseAuth.currentUser?.uid ?: return

        firebaseFirestore.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    currentUser = documentSnapshot.toObject(UserModel::class.java) ?: UserModel()
                    populateUserData(currentUser)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading user data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun populateUserData(user: UserModel) {
        binding.usernameTextView.text = user.username
        binding.emailTextView.text = user.email

        if (user.profilePic.isNullOrEmpty()) {
            Glide.with(this)
                .load(R.drawable.default_profile_pic)
                .into(binding.profileImageView)
        } else {
            Glide.with(this)
                .load(user.profilePic)
                .into(binding.profileImageView)
        }
    }
}

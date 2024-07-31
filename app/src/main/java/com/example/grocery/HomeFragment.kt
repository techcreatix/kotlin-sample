package com.example.grocery

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.SuperscriptSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.github.matech.imagepicker.ImagePicker
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeDrawable.TOP_START
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.grocery.grocersaver.GrocerSaver
import com.grocery.grocersaver.GrocerSaver.AndroidIdProvider.androidId
import com.grocery.grocersaver.R
import com.grocery.grocersaver.databinding.FragmentHomeBinding
import com.grocery.grocersaver.databinding.LayoutAddItemBottomSheetBinding
import com.grocery.grocersaver.databinding.LayoutProductDetailActionBinding
import com.grocery.grocersaver.enums.DashboardCategoryEnums
import com.grocery.grocersaver.models.ProductResponse
import com.grocery.grocersaver.models.productbarcode.Product
import com.grocery.grocersaver.ui.FragmentBase
import com.grocery.grocersaver.utils.ProgressDialog
import com.grocery.grocersaver.utils.Resource
import com.grocery.grocersaver.utils.Response
import com.grocery.grocersaver.utils.getCurrentTimestamp
import com.grocery.grocersaver.utils.getTimeStampIntoExpiryFormat
import com.grocery.grocersaver.utils.hideKeyboard
import com.grocery.grocersaver.utils.navigate
import com.grocery.grocersaver.utils.remove
import com.grocery.grocersaver.utils.setOnSingleClickListener
import com.grocery.grocersaver.utils.setScrollDisablingTouchListener
import com.grocery.grocersaver.utils.timeStampToCreatedAtFormat
import com.grocery.grocersaver.utils.visible
import com.grocery.grocersaver.viewmodel.HomeViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@ExperimentalBadgeUtils

class HomeFragment : FragmentBase() {
    private lateinit var binding: FragmentHomeBinding
    var badge: BadgeDrawable? = null
    private var expiryTimeStamp: Long? = null
    private var isAllowToOpen = true
    private lateinit var addItemBottomSheetBinding: LayoutAddItemBottomSheetBinding
    private lateinit var progressDialog: ProgressDialog


    private val viewmodel by viewModels<HomeViewModel>()
    private val barcodeLauncher: ActivityResultLauncher<ScanOptions?> = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            isAllowToOpen = true

            binding.loader.remove()

        } else {
            result.contents?.let {
                viewmodel.getRecipeSteps(it)
            }

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_home, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loader.setOnClickListener {

        }
        try {

            if (activity?.intent?.hasExtra("notification") == true) {
                navigate(HomeFragmentDirections.actionNavigationHomeToNotificationsFragment())
                activity?.intent?.removeExtra("notification")


            }
        } catch (e: Exception) {
            e.stackTrace
        }
        binding.apply {
            val mStringSpan =
                SpannableStringBuilder(resources.getString(R.string.tv_reducing_food_waste_lowers_co_emissions))
            mStringSpan.setSpan(SuperscriptSpan(), 29, 30, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            mStringSpan.setSpan(RelativeSizeSpan(0.7f), 29, 30, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            tvReducingFoodWaste.text = mStringSpan

            fabQR.setOnSingleClickListener(debounceTime = 800) {

                if (isAllowToOpen) {
                    isAllowToOpen = false
                    scanBottomSheet()
                }
            }
            viewmodel.getStorageCategoryCounts()


            getNotificationsCount()
            getDashboardCategory()

            viewmodel.getBarcodeProductLiveData.observe(viewLifecycleOwner) { response ->

                when (response) {
                    is Resource.Error -> {
                        binding.loader.remove()
                        dismissProgressDialog()
                        isAllowToOpen = true

                        alertMessagePopup(
                            "Couldn't scan the barcode",
                            "",
                            R.drawable.ic_barcode,
                            type = "3"
                        )

                    }

                    is Resource.Loading -> {
//                        binding.loader.visibility = View.VISIBLE
                        showProgressDialog()
                    }
                    is Resource.Success -> {

//                        binding.loader.remove()
                        dismissProgressDialog()
                        if (!response.result?.product?.productName.isNullOrEmpty()) {
                            addItemBottomSheet(
                                response.result?.product
                            )
                        } else {
                            alertMessagePopup(
                                "Couldn't scan the barcode",
                                "",
                                R.drawable.ic_barcode,
                                type = "3"
                            )

                        }
                        isAllowToOpen = true
//                        lifecycleScope.launch {
//                            delay(4000)
//                            isAllowToOpen = true
//                        }

                    }
                }
            }
            ivNotifications.setOnClickListener {
                navigate(HomeFragmentDirections.actionNavigationHomeToNotificationsFragment())
            }
            uiClickListeners()
        }
    }

    private fun getDashboardCategory() {

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewmodel.storageCategoryCountLiveData.observe(viewLifecycleOwner) { response ->
                    binding.loader.isVisible = response is Response.Loading
                    when (response) {
                        is Response.Failure -> {

                        }

                        Response.Loading -> Unit
                        is Response.Success -> {
                            binding.apply {
                                tvFridgeItem.text = resources.getQuantityString(
                                    R.plurals.text_items,
                                    response.data[DashboardCategoryEnums.FRIDGE.string] ?: 0,
                                    response.data[DashboardCategoryEnums.FRIDGE.string] ?: 0
                                )
                                tvFreezerItem.text = resources.getQuantityString(
                                    R.plurals.text_items,
                                    response.data[DashboardCategoryEnums.FREEZER.string] ?: 0,
                                    response.data[DashboardCategoryEnums.FREEZER.string] ?: 0
                                )
                                tvPantryItem.text = resources.getQuantityString(
                                    R.plurals.text_items,
                                    response.data[DashboardCategoryEnums.PANTRY.string] ?: 0,
                                    response.data[DashboardCategoryEnums.PANTRY.string] ?: 0
                                )
                                tvUnorganizedItem.text = resources.getQuantityString(
                                    R.plurals.text_items,
                                    response.data[DashboardCategoryEnums.UNORGANIZED.string] ?: 0,
                                    response.data[DashboardCategoryEnums.UNORGANIZED.string] ?: 0
                                )
                            }
                        }
                    }
                }
            }
        }

    }

    private fun getNotificationsCount() {
        val database = Firebase.firestore.collection("Users")

        val query = database.document(androidId).collection("Notifications")
            .whereEqualTo("is_read", false)


        query.addSnapshotListener { querySnapshot, firebaseFirestoreException ->
            if (firebaseFirestoreException != null) {
                Log.w("Firestore", "Listen failed.", firebaseFirestoreException)
                return@addSnapshotListener
            }

            if (querySnapshot != null && !querySnapshot.isEmpty) {
                val count = querySnapshot.size()
                showBadgeOnNotificationIcon(count)
                // You can now use the 'count' variable as needed in your application
            } else {
                Log.d("Firestore", "No unread notifications found.")
                // Handle the case where there are no unread notifications
            }
        }


        // Register snapshot listeners for each category


    }

    private fun uiClickListeners() {
        binding.apply {
            cvFridge.setOnClickListener {
                navigate(
                    HomeFragmentDirections.actionNavigationHomeToCategoryItemListingFragment(
                        "fridge",
                        "Fridge"
                    )
                )
            }
            cvFreezer.setOnClickListener {
                navigate(
                    HomeFragmentDirections.actionNavigationHomeToCategoryItemListingFragment(
                        "freezer",
                        "Freezer"
                    )
                )
            }
            cvPantry.setOnClickListener {
                navigate(
                    HomeFragmentDirections.actionNavigationHomeToCategoryItemListingFragment(
                        "pantry",
                        "Pantry"
                    )
                )
            }
            cvUnOrganized.setOnClickListener {
                navigate(
                    HomeFragmentDirections.actionNavigationHomeToCategoryItemListingFragment(
                        "unorganized",
                        "Unorganized"
                    )
                )
            }
        }

    }

    private fun addItemBottomSheet(response: Product?) {
        if (response != null) {

            val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialog)


            addItemBottomSheetBinding = LayoutAddItemBottomSheetBinding.inflate(layoutInflater)
            val view = addItemBottomSheetBinding.root
            dialog.dismissWithAnimation = true
            dialog.setContentView(view)
            val bottomSheetBehavior = BottomSheetBehavior.from(view.parent as View)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

            dialog.setOnCancelListener {
                categoryViewModel.clearViewModel()
                addItemBottomSheetBinding.loader.remove()

                dialog.dismiss()
            }
            addItemBottomSheetBinding.ivBack.setOnClickListener {
                categoryViewModel.clearViewModel()

                dialog.dismiss()
            }
            dialog.setOnDismissListener {
//                binding.loader.visibility = View.GONE
                dismissProgressDialog()
            }

            addItemBottomSheetBinding.apply {
                etNutritionDetail.setScrollDisablingTouchListener()
                tvSelectStorage.visible()
                Glide.with(requireContext())
                    .load(response.imageUrl ?: "")
                    .error(R.drawable.ic_save_environment)
                    .into(ivProduct)
                etItemName.setText(response.productName)
                ivProduct.setOnSingleClickListener {
                    hideKeyboard(requireActivity())
                    showImageSelectorBottomSheet {
                        addItemBottomSheetBinding.loader.isVisible = !it
                    }
                }

                tvExpiryDate.text = response.expirationDate
                categoryViewModel.storageTemperatureLiveData.observe(viewLifecycleOwner) {
//                    binding.loader.visibility = View.GONE
                    dismissProgressDialog()
                    tvStorageTemp.text = it
                }
                etWeight.setText(response.weight)

                categoryViewModel.foodCategoryLiveData.observe(viewLifecycleOwner) {
                    tvFoodCategory.text = it
                }
                categoryViewModel.expiryDateLiveData.observe(viewLifecycleOwner) { timestamp ->
                    expiryTimeStamp = timestamp
                    timestamp?.let {
                        val date = Date(it)

                        // Format the date
                        val sdf = SimpleDateFormat("dd, MMM yyyy", Locale.ENGLISH)
                        val formattedDate = sdf.format(date)
                        tvExpiryDate.text = formattedDate
                    }
                }
                categoryViewModel.storageCategoryLiveData.observe(viewLifecycleOwner) {
                    tvSelectStorage.text = it
                }
                tvFoodCategory.setOnSingleClickListener {
                    foodCategoryAlert()
                }
                tvStorageTemp.setOnSingleClickListener {
                    foodStorageTemperature()
                }
                tvExpiryDate.setOnSingleClickListener {
                    expiryCalendar()
                }
                addButton.text = "Add to Inventory"
                addButton.setOnSingleClickListener(debounceTime = 4000) {
                    addButton.isClickable = false
//                    addItemBottomSheetBinding.loader.visible()

                    validateAddProduct(addItemBottomSheetBinding, response, dialog)

                }
                tvSelectStorage.setOnSingleClickListener {
                    selectStorageAlert()
                }
            }


            dialog.setCancelable(true)

            setWhiteNavigationBar(dialog)
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            dialog.show()
        }
    }

    private fun validateAddProduct(
        bottomSheetBinding: LayoutAddItemBottomSheetBinding,
        response: Product?,
        dialog: BottomSheetDialog
    ) {
        val itemName = addItemBottomSheetBinding.etItemName.text!!.trim().toString()
        val price = addItemBottomSheetBinding.etPrice.text!!.trim().toString()
        val storageTemp = addItemBottomSheetBinding.tvStorageTemp.text.toString()
        val foodCategory = addItemBottomSheetBinding.tvFoodCategory.text.toString()
        val weight = addItemBottomSheetBinding.etWeight.text!!.trim().toString()
        val quantity = addItemBottomSheetBinding.etQuantity.text!!.trim().toString()
        val expiryDate = addItemBottomSheetBinding.tvExpiryDate.text.toString()
        val drawable = addItemBottomSheetBinding.ivProduct.drawable
        val storage = addItemBottomSheetBinding.tvSelectStorage.text.trim().toString()
        viewLifecycleOwner.lifecycleScope.launchWhenCreated {

            viewmodel.validationErrors.observe(viewLifecycleOwner) {
                addItemBottomSheetBinding.apply {
                    addButton.isClickable = it.any { true }
//                    loader.remove()

                    tvNameError.isVisible = !it["itemName"].isNullOrEmpty()
                    tvPriceError.isVisible = !it["price"].isNullOrEmpty()
                    tvStorageError.isVisible = !it["storage"].isNullOrEmpty()
                    tvFoodCategoryError.isVisible =
                        !it["foodCategory"].isNullOrEmpty()
                    tvWeightError.isVisible = !it["weight"].isNullOrEmpty()
                    tvQuantityError.isVisible = !it["quantity"].isNullOrEmpty()
                    tvExpiryDateError.isVisible = !it["expiryDate"].isNullOrEmpty()
                    tvImageError.isVisible = !it["drawable"].isNullOrEmpty()
                    tvTempError.isVisible = !it["storageTemp"].isNullOrEmpty()


                    tvNameError.text = it["itemName"]
                    tvPriceError.text = it["price"]
                    tvStorageError.text = it["storage"]
                    tvFoodCategoryError.text = it["foodCategory"]
                    tvWeightError.text = it["weight"]
                    tvQuantityError.text = it["quantity"]
                    tvExpiryDateError.text = it["expiryDate"]
                    tvImageError.text = it["drawable"]
                    tvTempError.text = it["storageTemp"]
                }
            }
        }
        if (viewmodel.validateProduct(
                itemName,
                price,
                storageTemp,
                foodCategory,
                weight,
                quantity,
                expiryDate,
                drawable,
                storage
            )
        ) {
            addItemBottomSheetBinding.loader.visibility = View.VISIBLE
            val bitmap = (bottomSheetBinding.ivProduct.drawable as BitmapDrawable).bitmap
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val data = baos.toByteArray()
            val originalBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            //  val resizedBitmap = resizeBitmap(originalBitmap)

            val outputStream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            val resizedByteArray = outputStream.toByteArray()

            val imageRef =
                Firebase.storage.reference.child("product_images/${getCurrentTimestamp()}.jpg")
            imageRef.putBytes(resizedByteArray).addOnSuccessListener { uploadTask ->
                val path = uploadTask.storage.path

                val encodedPath = com.grocery.grocersaver.utils.convertPathToURL(path)
                if (!encodedPath.isNullOrEmpty()) {

                    val timeStamp = getCurrentTimestamp()

                    val productResponse = ProductResponse(
                        timeStamp.toString(),
                        "",
                        timeStampToCreatedAtFormat(timeStamp),
                        bottomSheetBinding.tvFoodCategory.text.toString().trim(),

                        isDeleted = false,
                        isScanned = true,
                        nutritionDetails = bottomSheetBinding.etNutritionDetail.text.toString()
                            .trim(),
                        productExpiry = getTimeStampIntoExpiryFormat(expiryTimeStamp ?: 0L),
                        productImage = encodedPath,
                        productName = bottomSheetBinding.etItemName.text.toString(),
                        productQuantity = bottomSheetBinding.etQuantity.text.toString().toLong(),
                        consumedQuantity = 0,
                        expiredQuantity = 0,
                        productWeight = bottomSheetBinding.etWeight.text.toString(),
                        storageCategory = bottomSheetBinding.tvSelectStorage.text.toString(),
                        storageTemperature = bottomSheetBinding.tvStorageTemp.text.toString(),
                        updatedAt = timeStampToCreatedAtFormat(timeStamp),
                        productPrice = bottomSheetBinding.etPrice.text.toString()
                    )
                    val productsCollection = Firebase.firestore.collection("Users").document(
                        GrocerSaver.AndroidIdProvider.androidId
                    ).collection("Products")

                    // Generate a unique ID for the product
                    val productId = productsCollection.document().id

                    productResponse.productId = productId

                    val productRef = productsCollection.document(productId)

                    productRef.set(productResponse).addOnSuccessListener {
                        binding.loader.remove()

                        categoryViewModel.clearViewModel()

                        dialog.dismiss()
                        alertMessagePopup(
                            "Item added",
                            "",
                            R.drawable.ic_tick_success
                        )
                        addItemBottomSheetBinding.loader.remove()
                        lifecycleScope.launch {
                            delay(3500)
                            bottomSheetBinding.addButton.isClickable = true

                        }
                    }.addOnFailureListener {
                        binding.loader.remove()
                    }
                }

            }.addOnFailureListener {


            }


        } else {
            bottomSheetBinding.addButton.isClickable = true
            binding.loader.remove()
        }

    }

    override fun onResume() {

        super.onResume()
    }


    private fun scanBottomSheet() {

        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialog)
        val bottomSheetBinding = LayoutProductDetailActionBinding.inflate(layoutInflater)
        val view = bottomSheetBinding.root
        dialog.dismissWithAnimation = true
        dialog.setContentView(view)
        val bottomSheetBehavior = BottomSheetBehavior.from(view.parent as View)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        dialog.setOnCancelListener {
            isAllowToOpen = true
            dialog.dismiss()
        }


        bottomSheetBinding.apply {
            tvExpired.apply {
                text = "Scan Barcode"
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_barcode, 0, 0, 0)
            }
            tvConsumed.apply {
                text = "Scan History"
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_scan_history, 0, 0, 0)
            }
            tvExpired.setOnClickListener {
                isAllowToOpen = false
                val options = ScanOptions()
                options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                options.setPrompt("Scan a Bar Code")
                options.setCameraId(0) // Use a specific camera of the device
                options.setOrientationLocked(true)
                options.setBeepEnabled(false)
                options.setBarcodeImageEnabled(true)
                barcodeLauncher.launch(options)
                dialog.dismiss()


            }
            tvConsumed.setOnClickListener {
                isAllowToOpen = true
                dialog.dismiss()
                navigate(
                    HomeFragmentDirections.actionNavigationHomeToAnalyticsItemsFragment(
                        "Scan History",
                        "is_scanned",
                        0
                    )
                )
            }
            tvDelete.remove()
            viewConsumed.remove()

        }


        dialog.setCancelable(true)
        setWhiteNavigationBar(dialog)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        dialog.show()
    }

    private fun showBadgeOnNotificationIcon(badgeCount: Int) {
        try {
            if (::binding.isInitialized) {
                if (badgeCount != 0) {
                    badge = BadgeDrawable.create(requireContext())

                    binding.ivNotifications.viewTreeObserver.addOnGlobalLayoutListener(object :
                        ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {

                            badge?.let { badges ->
                                badges.apply {

                                    badgeGravity = TOP_START
                                    horizontalOffset = 30
                                    verticalOffset = 15
                                    number = badgeCount

                                    BadgeUtils.attachBadgeDrawable(
                                        this,
                                        binding.ivNotifications,
                                        null
                                    )

                                    if (badgeCount == 0) {
                                        isVisible = false

                                        BadgeUtils.detachBadgeDrawable(
                                            null,
                                            binding.ivNotifications
                                        )
                                        badges.clearNumber()
                                        alpha = 0

                                        BadgeUtils.detachBadgeDrawable(
                                            this,
                                            binding.ivNotifications
                                        )
                                        badge = null
                                    }
                                    BadgeUtils.attachBadgeDrawable(
                                        this,
                                        binding.ivNotifications,
                                        null
                                    )
                                }
                            }
                            binding.ivNotifications.viewTreeObserver.removeOnGlobalLayoutListener(
                                this
                            )
                        }
                    })
                } else {
                    badge = null
                    BadgeUtils.detachBadgeDrawable(badge, binding.ivNotifications)


                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        addItemBottomSheetBinding.loader.remove()

        when (resultCode) {
            Activity.RESULT_OK -> {
                val fileUri = data?.data
                fileUri?.let {
                    Glide.with(requireContext())
                        .load(it)
                        .error(R.drawable.ic_save_environment)
                        .into(addItemBottomSheetBinding.ivProduct)
                }
                addItemBottomSheetBinding.loader.remove()

            }

            ImagePicker.RESULT_ERROR -> {
                binding.loader.remove()
                addItemBottomSheetBinding.loader.remove()

                Toast.makeText(requireActivity(), ImagePicker.getError(data), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun showProgressDialog() {
        progressDialog = ProgressDialog()
        progressDialog.show(parentFragmentManager, "progress_dialog")
    }

    private fun dismissProgressDialog() {
        if (this::progressDialog.isInitialized && progressDialog.isVisible) {
            progressDialog.dismiss()
        }
    }

}
package xyz.aprildown.timer.flavor.google

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import xyz.aprildown.timer.app.base.data.FlavorData
import xyz.aprildown.timer.component.key.PreferenceStyleListFragment
import xyz.aprildown.timer.domain.utils.AppTracker
import xyz.aprildown.timer.flavor.google.databinding.FragmentBillingBinding
import xyz.aprildown.tools.anko.newTask
import xyz.aprildown.tools.anko.snackbar
import xyz.aprildown.tools.arch.observeEvent
import xyz.aprildown.tools.helper.IntentHelper
import xyz.aprildown.tools.helper.createChooserIntentIfDead
import xyz.aprildown.tools.helper.gone
import xyz.aprildown.tools.helper.show
import xyz.aprildown.tools.helper.startActivitySafely
import javax.inject.Inject

@AndroidEntryPoint
internal class BillingFragment : Fragment(R.layout.fragment_billing) {

    @Inject
    lateinit var flavorData: FlavorData

    @Inject
    lateinit var appTracker: AppTracker

    private lateinit var billingSupervisor: BillingSupervisor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        billingSupervisor = BillingSupervisor(
            requireContext(),
            requireSkuDetails = true,
            requestProState = true,
            requestBackupSubState = true,
            // consumeInAppPurchases = true,
        )
        billingSupervisor.withLifecycleOwner(this)
        billingSupervisor.supervise()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentBillingBinding.bind(view)

        if (savedInstanceState == null) {
            setUpEntries()
        }

        setUpBillingStates(binding)
    }

    private fun setUpBillingStates(binding: FragmentBillingBinding) {
        val context = binding.root.context

        billingSupervisor.proState.observe(viewLifecycleOwner) { hasPro ->
            TransitionManager.beginDelayedTransition(binding.cardPro)

            billingSupervisor.proSkuDetails.removeObservers(viewLifecycleOwner)
            if (hasPro) {
                binding.textProPrice.gone()
                binding.btnProPurchase.isEnabled = false
                binding.btnProPurchase.setText(R.string.billing_owned)
                binding.btnProPurchase.setOnClickListener(null)
            } else {
                binding.textProPrice.gone()
                binding.btnProPurchase.isEnabled = false
                binding.btnProPurchase.setText(R.string.billing_under_maintenance)
                binding.btnProPurchase.setOnClickListener(null)
                // billingSupervisor.proSkuDetails.observe(viewLifecycleOwner) { proSkuDetails ->
                //     TransitionManager.beginDelayedTransition(binding.cardPro)
                //
                //     binding.textProPrice.show()
                //     binding.textProPrice.text = proSkuDetails.price
                //     binding.btnProPurchase.isEnabled = true
                //     binding.btnProPurchase.setText(R.string.billing_purchase)
                //     binding.btnProPurchase.setOnClickListener {
                //         billingSupervisor.launchBillingFlow(requireActivity(), proSkuDetails)
                //     }
                // }
            }
        }
        billingSupervisor.goProEvent.observeEvent(viewLifecycleOwner) {
            binding.root.snackbar(R.string.thanks)
        }

        billingSupervisor.backupSubState.observe(viewLifecycleOwner) { hasBackupSub ->
            TransitionManager.beginDelayedTransition(binding.cardBackupSub)

            billingSupervisor.backupSubSkuDetails.removeObservers(viewLifecycleOwner)
            if (hasBackupSub) {
                binding.textBackupSubPrice.gone()
                binding.btnBackupSubSubscribe.isEnabled = false
                binding.btnBackupSubSubscribe.setText(R.string.billing_owned)
                binding.btnBackupSubManage.show()
                binding.textBackupSubCancelAnytime.gone()
                binding.textBackupSubPpTos.gone()
            } else {
                binding.textBackupSubPrice.gone()
                binding.btnBackupSubSubscribe.isEnabled = false
                binding.btnBackupSubSubscribe.setText(R.string.billing_under_maintenance)
                binding.btnBackupSubManage.show()
                binding.textBackupSubCancelAnytime.gone()
                binding.textBackupSubPpTos.gone()
                // billingSupervisor.backupSubSkuDetails.observe(viewLifecycleOwner) { backupSubSkuDetails ->
                //     TransitionManager.beginDelayedTransition(binding.cardBackupSub)
                //
                //     binding.textBackupSubPrice.show()
                //     binding.textBackupSubPrice.text = buildString {
                //         append(backupSubSkuDetails.price)
                //         // https://developer.android.com/reference/com/android/billingclient/api/SkuDetails#getsubscriptionperiod
                //         if (backupSubSkuDetails.subscriptionPeriod == "P1Y") {
                //             append(getString(R.string.billing_per_year))
                //         }
                //     }
                //     binding.btnBackupSubSubscribe.isEnabled = true
                //     binding.btnBackupSubSubscribe.setText(R.string.billing_subscribe)
                //     binding.btnBackupSubSubscribe.setOnClickListener {
                //         billingSupervisor.launchBillingFlow(requireActivity(), backupSubSkuDetails)
                //     }
                //     binding.btnBackupSubManage.gone()
                //     binding.textBackupSubCancelAnytime.show()
                //     binding.textBackupSubPpTos.show()
                //     updatePpTosText(binding.textBackupSubPpTos)
                // }
            }
        }
        binding.btnBackupSubManage.setOnClickListener {
            startActivitySafely(
                IntentHelper.webPage(billingSupervisor.getManageSubscriptionLink()).newTask()
            )
        }
        billingSupervisor.backupSubEvent.observeEvent(viewLifecycleOwner) {
            binding.root.snackbar(R.string.thanks)
        }

        billingSupervisor.error.observeEvent(viewLifecycleOwner) {
            context.showErrorDialog(it)
        }
    }

    // private fun updatePpTosText(textView: TextView) {
    //     val context = textView.context
    //     val contentTemplate = getString(R.string.billing_explanation)
    //     val privacyPolicy = getString(R.string.privacy_policy)
    //     val terms = getString(R.string.terms_of_service)
    //     val content = contentTemplate.format(privacyPolicy, terms)
    //     val spannable = SpannableString(content)
    //
    //     val privacyPolicyIndex = content.indexOf(privacyPolicy)
    //     spannable.setSpan(
    //         object : ClickableSpan() {
    //             override fun onClick(widget: View) {
    //                 context.openWebsiteWithWarning(Constants.getPrivacyPolicyLink())
    //             }
    //         },
    //         privacyPolicyIndex,
    //         privacyPolicyIndex + privacyPolicy.length,
    //         Spannable.SPAN_INCLUSIVE_EXCLUSIVE
    //     )
    //
    //     val termsIndex = content.indexOf(terms)
    //     spannable.setSpan(
    //         object : ClickableSpan() {
    //             override fun onClick(widget: View) {
    //                 context.openWebsiteWithWarning(Constants.getTermsOfServiceLink())
    //             }
    //         },
    //         termsIndex,
    //         termsIndex + terms.length,
    //         Spannable.SPAN_INCLUSIVE_EXCLUSIVE
    //     )
    //
    //     textView.run {
    //         show()
    //         setText(spannable, TextView.BufferType.SPANNABLE)
    //         movementMethod = LinkMovementMethod.getInstance()
    //     }
    // }

    private fun setUpEntries() {
        childFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainerOneTime,
                PreferenceStyleListFragment.newInstance(
                    PreferenceStyleListFragment.Entry(
                        R.drawable.settings_theme,
                        R.string.billing_more_themes_title,
                        R.string.billing_more_themes_desp
                    ),
                    PreferenceStyleListFragment.Entry(
                        R.drawable.settings_count,
                        R.string.billing_baked_count_title,
                        R.string.billing_baked_count_desp
                    ),
                    PreferenceStyleListFragment.Entry(
                        R.drawable.settings_code,
                        R.string.billing_future_title,
                        R.string.billing_future_desp
                    ),
                )
            )
            .commit()
        childFragmentManager.beginTransaction()
            .replace(
                R.id.fragmentContainerCloudBackup,
                PreferenceStyleListFragment.newInstance(
                    PreferenceStyleListFragment.Entry(
                        R.drawable.settings_cloud_backup,
                        R.string.billing_cloud_backup_title,
                        R.string.billing_cloud_backup_desp
                    )
                )
            )
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.flavor_help, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_flavor_help -> {
                val context = requireContext()
                MaterialAlertDialogBuilder(context)
                    .setItems(
                        arrayOf(
                            getString(R.string.billing_help_contact),
                        )
                    ) { _, which ->
                        when (which) {
                            0 -> {
                                startActivitySafely(
                                    IntentHelper.email(
                                        email = flavorData.email,
                                        subject = getString(R.string.billing_help_email_title)
                                    ).createChooserIntentIfDead(context)
                                )
                            }
                        }
                    }
                    .show()
            }
            else -> return false
        }
        return true
    }
}

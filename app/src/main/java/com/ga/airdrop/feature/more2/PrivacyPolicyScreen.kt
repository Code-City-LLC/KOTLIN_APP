package com.ga.airdrop.feature.more2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.Spacing

private data class PrivacySection(val id: String, val title: String, val content: String)

// Verbatim copy of the RN `sectionsData` constant (PrivacyPolicyView) — the
// fallback rendered before /content/privacy-policy responds.
private val PRIVACY_SECTIONS = listOf(
    PrivacySection(
        id = "overview",
        title = "Overview",
        content = "This Privacy Policy is established by AirDrop Ltd. to explain how we " +
            "protect, collect, and use your personal information. This Privacy Policy also " +
            "describes your choices regarding your personal information. “Personal " +
            "information” means personally identifiable information such as information " +
            "about you that is not publicly available, including your name, address, phone " +
            "number, and email address. This Privacy Policy applies to the services under " +
            "www.airdropja.com owned and operated by AirDrop Ltd. AirDrop, Inc. is the " +
            "controller of your personal data. By visiting the website, you agree to the " +
            "terms of this Privacy Policy. If you do not agree with the terms of this " +
            "Privacy Policy, please do not use the website. This Privacy Policy is " +
            "incorporated into the AirDrop User Agreement. AirDrop reserves the right to " +
            "modify this Privacy Policy frequently.",
    ),
    PrivacySection(
        id = "personal-data",
        title = "Personal Data we collect",
        content = "We collect personal information that you provide directly to us when you " +
            "register for an account, use our services, make a purchase, or communicate with " +
            "us. This may include your name, email address, phone number, mailing address, " +
            "payment information, and any other information you choose to provide. We also " +
            "collect information automatically when you use our services, including your IP " +
            "address, device information, browser type, operating system, and usage data. " +
            "Additionally, we may collect information from third-party sources, such as " +
            "social media platforms, if you choose to connect your account or use social " +
            "login features.",
    ),
    PrivacySection(
        id = "log-files",
        title = "Log Files",
        content = "Like many websites and mobile applications, we automatically collect " +
            "certain information and store it in log files. This information includes IP " +
            "addresses, browser type, Internet Service Provider (ISP), referring/exit pages, " +
            "operating system, date/time stamp, and clickstream data. We use this " +
            "information to analyze trends, administer the site, track user movements " +
            "around the site, and gather demographic information about our user base as a " +
            "whole. Log files help us understand how visitors interact with our services, " +
            "diagnose technical problems, and improve the overall user experience. This " +
            "data is typically aggregated and anonymized for analysis purposes.",
    ),
    PrivacySection(
        id = "tracking-technologies",
        title = "Tracking Technologies",
        content = "We use various tracking technologies, including cookies, web beacons, " +
            "pixels, and similar technologies to collect and store information about your " +
            "interactions with our services. Cookies are small text files that are placed " +
            "on your device when you visit our website. We use both session cookies, which " +
            "expire when you close your browser, and persistent cookies, which remain on " +
            "your device until deleted. These technologies help us remember your " +
            "preferences, understand how you use our services, personalize your experience, " +
            "and provide targeted advertising. You can control cookies through your browser " +
            "settings, but disabling cookies may limit your ability to use certain features " +
            "of our services.",
    ),
    PrivacySection(
        id = "behavioral-targeting",
        title = "Behavioral Targeting/Re-targeting",
        content = "We may use behavioral targeting and re-targeting technologies to deliver " +
            "personalized advertisements based on your browsing behavior and interests. " +
            "This means that we, or our advertising partners, may show you ads for products " +
            "or services that you have previously viewed or that we believe may be of " +
            "interest to you based on your online activity. These technologies may track " +
            "your visits to our website and other websites to build a profile of your " +
            "interests. You may see these targeted ads on our website, on other websites " +
            "you visit, or in mobile applications. You can opt out of behavioral targeting " +
            "by adjusting your browser settings, using opt-out tools provided by " +
            "advertising networks, or by contacting us directly. Please note that opting " +
            "out does not mean you will no longer see advertisements, but rather that the " +
            "ads you see will be less relevant to your interests.",
    ),
    PrivacySection(
        id = "how-we-use",
        title = "How we use your personal data",
        content = "We use the personal information we collect for various purposes, " +
            "including: providing, maintaining, and improving our services; processing " +
            "transactions and sending related information; sending technical notices, " +
            "updates, security alerts, and support messages; responding to your comments, " +
            "questions, and requests; providing customer service; monitoring and analyzing " +
            "trends, usage, and activities; personalizing and improving your experience; " +
            "developing new products and services; detecting, preventing, and addressing " +
            "technical issues and fraudulent or illegal activities; and complying with " +
            "legal obligations. We may also use your information to send you marketing " +
            "communications, promotional materials, and other information that may be of " +
            "interest to you, subject to your preferences and applicable law. You can opt " +
            "out of receiving marketing communications at any time by following the " +
            "unsubscribe instructions in the communications or by contacting us.",
    ),
    PrivacySection(
        id = "information-sharing",
        title = "Information Sharing and Disclosure",
        content = "We may share your personal information in the following circumstances: " +
            "with service providers and business partners who perform services on our " +
            "behalf, such as payment processing, data analysis, email delivery, hosting " +
            "services, and customer service; with third parties for marketing purposes, " +
            "subject to your preferences; in connection with a merger, acquisition, or " +
            "sale of assets, in which case your information may be transferred as part of " +
            "that transaction; to comply with legal obligations, respond to legal requests, " +
            "or protect our rights and the rights of others; with your consent or at your " +
            "direction; and in aggregated or anonymized form that cannot reasonably be used " +
            "to identify you. We do not sell your personal information to third parties. " +
            "We require all third parties to respect the security of your personal " +
            "information and to treat it in accordance with the law.",
    ),
    PrivacySection(
        id = "communications",
        title = "Communications from the Site",
        content = "We may send you various types of communications, including: transactional " +
            "emails related to your account, orders, or services you have requested; " +
            "marketing and promotional communications about our products, services, offers, " +
            "and events; newsletters and updates about our company and services; " +
            "service-related announcements, such as changes to our terms or policies; and " +
            "responses to your inquiries or requests. You can control your communication " +
            "preferences by updating your account settings, clicking the unsubscribe link " +
            "in our emails, or contacting us directly. Please note that even if you opt out " +
            "of marketing communications, we may still send you important transactional and " +
            "service-related messages that are necessary for the operation of your account " +
            "or the services you have requested.",
    ),
    PrivacySection(
        id = "links",
        title = "Links to Other Sites",
        content = "Our services may contain links to third-party websites, applications, or " +
            "services that are not owned or controlled by AirDrop. These links are provided " +
            "for your convenience and information. We are not responsible for the privacy " +
            "practices, content, or security of these third-party sites. We encourage you " +
            "to review the privacy policies and terms of service of any third-party sites " +
            "you visit. This Privacy Policy applies only to information collected by " +
            "AirDrop through our own services. When you click on a link to a third-party " +
            "site, you will be subject to that site’s privacy policy and terms of service. " +
            "We do not endorse or assume any responsibility for the content, privacy " +
            "policies, or practices of third-party websites or services.",
    ),
)

/**
 * Privacy Policy — Figma node 40001387:9042, behavior from
 * FigmaPrivacyPolicyViewController: accordion sections ("Overview" seeded
 * open) with the live /content/privacy-policy markdown/HTML swap-in.
 */
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
    viewModel: PrivacyPolicyViewModel = viewModel(),
) {
    val colors = AirdropTheme.colors
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.gray100)
    ) {
        More2InnerHeader(title = "Privacy Policy", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.md))

            val live = state.liveContent
            if (live != null) {
                More2OuterCard {
                    Column(Modifier.padding(Spacing.md)) {
                        LegalHtmlContent(live)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PRIVACY_SECTIONS.forEach { section ->
                        AccordionCard(
                            title = section.title,
                            expanded = section.id in state.expandedIds,
                            onToggle = { viewModel.toggle(section.id) },
                        ) {
                            Text(
                                text = section.content,
                                style = AirdropType.body2,
                                color = colors.textDescription,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * Layman Error Translator and Categorization for OmniDownloader.
 * Converts raw platform and network exceptions into clear explanations with recovery hints.
 */

export function formatLaymanError(errOrMsg) {
  let rawMsg = typeof errOrMsg === "string" ? errOrMsg : ((errOrMsg && errOrMsg.message) || String(errOrMsg));
  let code = (errOrMsg && errOrMsg.code) || "UNKNOWN";
  let hint = (errOrMsg && errOrMsg.hint) || "";
  let source = (errOrMsg && errOrMsg.source) || "platform";

  // Clean raw ANSI codes or URL dumps if present
  let clean = (rawMsg || "").replace(/\x1b\[[0-9;]*[a-zA-Z]/g, '').trim();
  const lower = clean.toLowerCase();

  let title = "Unable to Process Video";
  let laymanMessage = clean;

  // Platform/YouTube Specific Checks
  if (code === "BOT_CHECK" || lower.includes("not a bot") || lower.includes("bot verification") || lower.includes("sign in to confirm")) {
    source = "platform";
    code = "BOT_CHECK";
    title = "Platform Verification Required";
    laymanMessage = "The platform is asking to confirm you are a real person and not an automated program.";
    if (!hint) hint = "Verify the URL is publicly accessible or playable in your web browser.";
  } else if (code === "PRIVATE_VIDEO" || lower.includes("private video") || lower.includes("this video is private")) {
    source = "platform";
    code = "PRIVATE_VIDEO";
    title = "Private Video";
    laymanMessage = "The creator has set this video to private. It is only accessible to invited viewers.";
    if (!hint) hint = "Verify you have permission and are logged in in your default browser.";
  } else if (code === "AGE_RESTRICTED" || lower.includes("age-restricted") || lower.includes("confirm your age") || lower.includes("sign in to view")) {
    source = "platform";
    code = "AGE_RESTRICTED";
    title = "Age-Restricted Video";
    laymanMessage = "The platform requires a signed-in account over 18 to view this content.";
    if (!hint) hint = "Ensure the link is playable in your default web browser.";
  } else if (code === "GEO_BLOCKED" || lower.includes("available in your country") || lower.includes("geo-restricted") || lower.includes("blocked it in your country")) {
    source = "platform";
    code = "GEO_BLOCKED";
    title = "Regional Restriction (Geo-Blocked)";
    laymanMessage = "The content creator has restricted this video in your geographical location.";
    if (!hint) hint = "Connect through a VPN server in a country where this video is permitted and try again.";
  } else if (code === "MEMBERS_ONLY" || lower.includes("members-only") || lower.includes("members only") || lower.includes("join this channel")) {
    source = "platform";
    code = "MEMBERS_ONLY";
    title = "Channel Members Only";
    laymanMessage = "This video is exclusively available to paid members of the channel.";
    if (!hint) hint = "Verify that your account has active membership access to this channel.";
  } else if (code === "LIVE_STREAM" || lower.includes("is a live stream") || lower.includes("live event will begin") || lower.includes("premieres in")) {
    source = "platform";
    code = "LIVE_STREAM";
    title = "Live Stream or Upcoming Premiere";
    laymanMessage = "This video is currently streaming live or has not premiered yet.";
    if (!hint) hint = "Live broadcasts cannot be downloaded while streaming. Wait until the stream concludes.";
  } 
  // Internet / Network Specific Checks
  else if (code === "RATE_LIMITED" || lower.includes("http error 429") || lower.includes("too many requests") || lower.includes("rate-limit")) {
    source = "network";
    code = "RATE_LIMITED";
    title = "Platform Temporarily Busy (Rate Limited)";
    laymanMessage = "The video platform is temporarily limiting requests from your IP address.";
    if (!hint) hint = "Wait 2 to 3 minutes before trying again.";
  } else if (code === "CONNECTION_TIMEOUT" || lower.includes("timed out") || lower.includes("connection refused") || lower.includes("transporterror")) {
    source = "network";
    code = "CONNECTION_TIMEOUT";
    title = "Internet Connection Problem";
    laymanMessage = "Failed to establish a reliable connection to the video servers.";
    if (!hint) hint = "Check your internet connection, Wi-Fi signal, or DNS settings.";
  }

  return {
    title,
    message: laymanMessage,
    hint,
    code,
    source,
    rawText: clean
  };
}

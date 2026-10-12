const now = Date.now();
const names = [
  ["Sarah Ahmed", "sarah.ahmed@example.com", "ar"],
  ["Omar Hassan", "omar.h@example.com", "ar"],
  ["James Wilson", "james.w@example.com", "en"],
  ["Nour Ali", "nour.ali@example.com", "ar"],
  ["Emily Chen", "emily.c@example.com", "en"],
  ["Ahmed Khaled", "ahmed.k@example.com", "ar"],
  ["Laila Mostafa", "laila.m@example.com", "ar"],
  ["Daniel Smith", "daniel.s@example.com", "en"],
];
export const users = names.map(([name, email, language], i) => ({
  id: `usr_${1001 + i}`,
  name,
  email,
  language,
  lastSeen: now - (i % 3 === 0 ? 45000 : (i + 1) * 3600000),
  deviceCount: [2, 1, 3, 1, 2, 1, 1, 2][i],
  createdAt: now - (i + 8) * 86400000,
}));
export const devices = users.flatMap((u, i) =>
  Array.from({ length: u.deviceCount }, (_, j) => ({
    id: `device_${i}_${j}`,
    uid: u.id,
    model:
      j % 2
        ? "Samsung Galaxy S24"
        : [
            "iPhone 15 Pro",
            "Samsung Galaxy S24",
            "iPhone 14",
            "Google Pixel 8",
          ][i % 4],
    platform: j % 2 || i % 2 ? "android" : "ios",
    version: i === 3 ? "2.3.0" : "2.4.1",
    build: i === 3 ? 230 : 241,
    lastSeen: u.lastSeen,
    createdAt: u.createdAt,
  })),
);
const features = [
  ["Sign in", "POST", "/auth/login", 200, 120, "Authentication completed"],
  ["Browse products", "GET", "/products", 200, 84, "Products loaded"],
  [
    "Checkout",
    "POST",
    "/orders",
    500,
    2450,
    "Payment gateway timed out. Reference: PAY_TIMEOUT",
  ],
  ["Order tracking", "GET", "/orders/{id}", 200, 97, "Order status loaded"],
  [
    "Profile",
    "PATCH",
    "/profile",
    422,
    210,
    "Profile validation failed: missing display name",
  ],
  ["Search", "GET", "/search", 200, 63, "Search completed"],
  ["Notifications", "GET", "/notifications", 200, 76, "Inbox loaded"],
  ["Address book", "GET", "/addresses", 200, 112, "Addresses loaded"],
];
export const events = Array.from({ length: 24 }, (_, i) => {
  const [feature, method, path, status, durationMs, message] = features[i % 8];
  return {
    id: `evt_${1000 + i}`,
    uid: users[i % 8].id,
    feature,
    method,
    path,
    status,
    durationMs,
    message,
    input: { method, path, body: method === "GET" ? null : { itemId: `sample_${i % 4}`, quantity: 1 }, context: { screen: feature, language: users[i % 8].language } },
    output: { status, body: status >= 400 ? { error: { code: status===500 ? "PAY_TIMEOUT" : "VALIDATION_ERROR", message } } : { success: true, message } },
    createdAt: now - i * 420000,
  };
});
export const initialPolicy = {
  maintenance: false,
  maintenanceEn:
    "We are performing scheduled maintenance. Please try again shortly.",
  maintenanceAr: "التطبيق تحت الصيانة. يرجى المحاولة مرة أخرى بعد قليل.",
  updateEn: "A new version is available. Please update your app to continue.",
  updateAr: "يتوفر إصدار جديد. يرجى تحديث التطبيق للمتابعة.",
  android: {
    releases: [{build:240,version:"2.4.0",enabled:false},{build:241,version:"2.4.1",enabled:true}],
    minimumBuild: 240,
    latestBuild: 241,
    version: "2.4.1",
    storeUrl: "https://play.google.com/store/apps?id=com.example.app",
  },
  ios: {
    releases: [{build:240,version:"2.4.0",enabled:false},{build:241,version:"2.4.1",enabled:true}],
    minimumBuild: 240,
    latestBuild: 241,
    version: "2.4.1",
    storeUrl: "https://apps.apple.com/app/id123456789",
  },
};
// Kuwait time (UTC+3): National Day 25 Feb, Liberation Day 26 Feb.
export const initialOccasions = [
  {
    id: "demo_occasion_national_day",
    titleEn: "Kuwait National Day",
    titleAr: "العيد الوطني الكويتي",
    messageEn: "Happy National Day! Wishing Kuwait continued prosperity.",
    messageAr: "كل عام والكويت بخير بمناسبة العيد الوطني.",
    startAt: Date.UTC(2027, 1, 24, 21),
    endAt: Date.UTC(2027, 1, 25, 21),
    enabled: true,
    iconKey: "national_day",
    accentColor: "#007a3d",
    bannerUrl: null,
  },
  {
    id: "demo_occasion_liberation_day",
    titleEn: "Liberation Day",
    titleAr: "يوم التحرير",
    messageEn: "Celebrating Kuwait's Liberation Day.",
    messageAr: "نحتفل معكم بذكرى يوم التحرير.",
    startAt: Date.UTC(2027, 1, 25, 21),
    endAt: Date.UTC(2027, 1, 26, 21),
    enabled: true,
    iconKey: "liberation_day",
    accentColor: "#ce1126",
    bannerUrl: null,
  },
];
export const initialNotifications = [
  {
    id: "demo_notice_1",
    title: "Your next update is here",
    body: "Explore the latest improvements in our app.",
    userIds: users.slice(0, 4).map((u) => u.id),
    createdAt: now - 3600000,
    status: "simulated",
    accepted: 0,
    failed: 0,
  },
];

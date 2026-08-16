/**
 * Seeds the Food Delivery System with demo data for manual UI testing.
 *
 *   node scripts/seed-demo-data.js
 *
 * Everything goes through the API Gateway on :8080, so all services (plus the
 * registry and RabbitMQ) must be running first.
 *
 * Creates:
 *   - 1 demo customer          demo@fds.test / demo1234
 *   - 5 restaurants with menus (one deliberately closed, one item out of stock)
 *   - 3 delivery riders
 *   - 3 sample orders left in different states: delivered, cancelled+refunded,
 *     and one still in progress, so the "My orders" page has something to show.
 *
 * Safe to run more than once - it appends rather than resetting. Pass --orders-only
 * to skip restaurant creation when you just want more sample orders.
 */

const API = process.env.API || "http://localhost:8080";
const ordersOnly = process.argv.includes("--orders-only");

const DEMO_USER = {
  fullName: "Demo Customer",
  email: "demo@fds.test",
  password: "demo1234",
  phone: "01700000000"
};

const RESTAURANTS = [
  {
    name: "Kacchi Bhai", address: "Dhanmondi 27, Dhaka",
    cuisineType: "Bangladeshi", phone: "01711111111", available: true,
    menu: [
      ["Mutton Kacchi", "Aromatic basmati with slow-cooked mutton", "Main Course", 420, true],
      ["Beef Tehari", "Spiced short-grain rice with tender beef", "Main Course", 280, true],
      ["Chicken Roast", "Sweet-savoury roast, a biryani staple", "Main Course", 180, true],
      ["Borhani", "Spiced yoghurt drink", "Drinks", 60, true],
      ["Firni", "Rice pudding with cardamom", "Dessert", 90, true],
      ["Jali Kabab", "Minced beef patty, seasonal", "Starters", 150, false]
    ]
  },
  {
    name: "Sultan's Table", address: "Banani 11, Dhaka",
    cuisineType: "Mughlai", phone: "01722222222", available: true,
    menu: [
      ["Chicken Biryani", "Layered with saffron and fried onion", "Main Course", 320, true],
      ["Mutton Rezala", "Creamy white curry, slow simmered", "Main Course", 450, true],
      ["Shami Kabab", "Spiced minced beef patties", "Starters", 160, true],
      ["Butter Naan", "Clay-oven flatbread", "Sides", 40, true],
      ["Sweet Lassi", "Chilled yoghurt drink", "Drinks", 80, true]
    ]
  },
  {
    name: "Cafe Mango", address: "Gulshan 2, Dhaka",
    cuisineType: "Continental", phone: "01733333333", available: true,
    menu: [
      ["Grilled Chicken", "Herb-marinated with seasonal vegetables", "Main Course", 480, true],
      ["Mushroom Pasta", "Cream sauce with parmesan", "Main Course", 390, true],
      ["Caesar Salad", "Cos lettuce, croutons, shaved parmesan", "Starters", 260, true],
      ["Iced Latte", "Double shot over ice", "Drinks", 180, true],
      ["Chocolate Brownie", "Warm, with vanilla ice cream", "Dessert", 220, true]
    ]
  },
  {
    name: "Dhaka Street Bites", address: "Mirpur 10, Dhaka",
    cuisineType: "Street Food", phone: "01744444444", available: true,
    menu: [
      ["Fuchka", "Crisp shells with tamarind water", "Snacks", 120, true],
      ["Chotpoti", "Spiced chickpeas with boiled egg", "Snacks", 140, true],
      ["Jhalmuri", "Puffed rice with mustard oil", "Snacks", 60, true],
      ["Cha", "Milk tea", "Drinks", 25, true]
    ]
  },
  {
    // Deliberately closed, so the UI's "Closed" state can be demonstrated.
    name: "Nawab's Kitchen", address: "Old Dhaka, Chawkbazar",
    cuisineType: "Bangladeshi", phone: "01755555555", available: false,
    menu: [
      ["Morog Polao", "Fragrant chicken pilaf", "Main Course", 350, true],
      ["Bakarkhani", "Layered flaky bread", "Sides", 50, true]
    ]
  }
];

const RIDERS = [
  { name: "Karim Rahman", phone: "01799999991", vehicleType: "MOTORCYCLE" },
  { name: "Jamal Uddin", phone: "01799999992", vehicleType: "BICYCLE" },
  { name: "Rakib Hasan", phone: "01799999993", vehicleType: "MOTORCYCLE" }
];

/* ---------------------------------------------------------------- */

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function call(path, method = "GET", body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(API + path, opts);
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { }
  if (!res.ok) throw new Error(method + " " + path + " -> " + res.status + " " + text.slice(0, 160));
  return data;
}

/** Waits for an async RabbitMQ step to land, instead of guessing with a fixed sleep. */
async function waitFor(fn, ok, tries = 20, gap = 700) {
  for (let i = 0; i < tries; i++) {
    try { const v = await fn(); if (ok(v)) return v; } catch (_) { }
    await sleep(gap);
  }
  return null;
}

async function main() {
  console.log("Seeding via " + API + "\n");

  // ---- customer -------------------------------------------------
  let user;
  try {
    user = await call("/api/users/register", "POST", DEMO_USER);
    console.log("customer   created   " + DEMO_USER.email);
  } catch (e) {
    const login = await call("/api/users/login", "POST",
      { email: DEMO_USER.email, password: DEMO_USER.password });
    user = login.user || { id: login.userId || login.id };
    console.log("customer   exists    " + DEMO_USER.email);
  }
  if (!user || !user.id) throw new Error("could not resolve the demo customer id");

  // ---- restaurants ----------------------------------------------
  const created = [];
  if (!ordersOnly) {
    for (const r of RESTAURANTS) {
      const saved = await call("/restaurants/", "POST", {
        name: r.name, address: r.address, cuisineType: r.cuisineType,
        phone: r.phone, available: r.available
      });
      for (const [name, description, category, price, available] of r.menu) {
        await call("/restaurants/" + saved.id + "/menu", "POST",
          { name, description, category, price, available });
      }
      created.push({ ...saved, menu: r.menu });
      console.log("restaurant created   " + r.name.padEnd(20) + r.menu.length + " items" +
                  (r.available ? "" : "  (closed)"));
    }

    for (const rider of RIDERS) {
      const saved = await call("/api/riders", "POST", rider);
      console.log("rider      created   " + rider.name + "  " + saved.id);
    }
  }

  // ---- sample orders --------------------------------------------
  const pool = created.length ? created : await call("/restaurants/");
  const shop = pool.find(r => r.available !== false) || pool[0];
  const full = await call("/restaurants/" + shop.id);
  const dishes = (full.menuItems || []).filter(i => i.available);
  if (dishes.length < 2) { console.log("\nnot enough dishes to build sample orders"); return; }

  console.log("");
  const place = async lines => {
    const items = lines.map(([dish, qty]) => ({
      menuItemId: dish.id, name: dish.name, unitPrice: dish.price, quantity: qty
    }));
    const total = items.reduce((n, i) => n + i.unitPrice * i.quantity, 0);
    return call("/orders/", "POST", {
      customerId: user.id,
      restaurantId: full.id,
      restaurantName: full.name,
      productId: items[0].menuItemId,
      quantity: items.reduce((n, i) => n + i.quantity, 0),
      price: total,
      items,
      deliveryAddress: "House 12, Road 5, Dhanmondi, Dhaka",
      pickupAddress: full.address
    });
  };

  // 1) a completed order, walked all the way to DELIVERED
  const o1 = await place([[dishes[0], 1], [dishes[1], 2]]);
  console.log("order      placed    " + o1.id + "  -> will be DELIVERED");
  const d1 = await waitFor(() => call("/api/deliveries/order/" + o1.id), d => d && d.id);
  if (d1) {
    const riders = await call("/api/riders/available");
    if (riders.length) {
      await call("/api/deliveries/" + d1.id + "/assign", "POST", { riderId: riders[0].id });
      await call("/api/deliveries/" + d1.id + "/status", "PUT", { status: "PICKED_UP" });
      await call("/api/deliveries/" + d1.id + "/status", "PUT", { status: "DELIVERED" });
      console.log("           delivered by " + riders[0].name);
    }
  }

  // 2) a cancelled order - exercises the refund + delivery-cancellation chain
  const o2 = await place([[dishes[0], 1]]);
  console.log("order      placed    " + o2.id + "  -> will be CANCELLED + REFUNDED");
  await waitFor(() => call("/orders/" + o2.id), o => o && o.status === "CONFIRMED");
  await call("/orders/" + o2.id + "/cancel", "POST", { reason: "Ordered by mistake" });

  // 3) one left in flight, so the tracker shows a live order
  const o3 = await place([[dishes[1], 1], [dishes[dishes.length - 1], 1]]);
  console.log("order      placed    " + o3.id + "  -> left in progress");

  console.log("\nDone. Sign in at http://localhost:5173");
  console.log("   email     " + DEMO_USER.email);
  console.log("   password  " + DEMO_USER.password);
}

main().catch(e => {
  console.error("\nSeed failed: " + e.message);
  console.error("Is the API gateway up on :8080 with all services registered?");
  process.exit(1);
});

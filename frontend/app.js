/* ===========================================================
   Food Delivery System — customer storefront.

   Every call goes through the API Gateway on :8080. The frontend never
   talks to a service port directly - that is the whole point of having a
   gateway, and it means service ports can move without touching this file.
   =========================================================== */

const API = "http://localhost:8080";

/* ---------------- state ---------------- */

const store = {
  user: JSON.parse(localStorage.getItem("fds.user") || "null"),
  token: localStorage.getItem("fds.token") || "",
  cart: JSON.parse(localStorage.getItem("fds.cart") || "null") || { restaurant: null, lines: [] },
  view: "home",
  cuisine: "All",      // chip filter - asks Restaurant Service to do the filtering
  cuisines: [],        // every cuisine seen on the unfiltered list, for the chips
  q: "",               // free-text search, matched in the browser
  restaurants: [],
  current: null,
  orders: [],
  detail: {}          // orderId -> { payment, delivery }
};

const save = () => {
  localStorage.setItem("fds.user", JSON.stringify(store.user));
  localStorage.setItem("fds.token", store.token);
  localStorage.setItem("fds.cart", JSON.stringify(store.cart));
};

/* ---------------- tiny helpers ---------------- */

const $ = s => document.querySelector(s);
const app = () => $("#app");
const money = n => "৳" + Number(n || 0).toFixed(2);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c =>
  ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function toast(msg, bad) {
  const t = document.createElement("div");
  t.className = "toast" + (bad ? " bad" : "");
  t.textContent = msg;
  $("#toast").appendChild(t);
  setTimeout(() => t.remove(), 3200);
}

/* Deterministic warm colour per restaurant, so cards look varied without images. */
const TONES = ["#B4451F", "#8E6A1F", "#3C6B4A", "#7A3B52", "#2F5D6B", "#93521C"];
const toneOf = s => TONES[[...String(s)].reduce((a, c) => a + c.charCodeAt(0), 0) % TONES.length];

async function api(path, { method = "GET", body, auth } = {}) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  if (auth && store.token) opts.headers["Authorization"] = "Bearer " + store.token;

  let res;
  try {
    res = await fetch(API + path, opts);
  } catch (e) {
    throw new Error("Cannot reach the API gateway on :8080 — is it running?");
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { }

  if (!res.ok) {
    let msg = (data && (data.message || data.error)) || ("Request failed (" + res.status + ")");
    // Bean Validation failures carry the useful detail in validationErrors
    // ({ password: "Password must be at least 8 characters" }); without this the
    // user only ever sees the generic "Validation failed".
    const fieldErrors = data && data.validationErrors;
    if (fieldErrors && typeof fieldErrors === "object") {
      const detail = Object.keys(fieldErrors)
        .map((field) => field + ": " + fieldErrors[field])
        .join(", ");
      if (detail) msg += " — " + detail;
    }
    throw new Error(msg);
  }
  return data;
}

/* ---------------- cart ---------------- */

const cartCount = () => store.cart.lines.reduce((n, l) => n + l.quantity, 0);
const cartTotal = () => store.cart.lines.reduce((n, l) => n + l.quantity * l.unitPrice, 0);

function addToCart(restaurant, item) {
  if (store.cart.restaurant && store.cart.restaurant.id !== restaurant.id) {
    if (!confirm("Your cart has items from " + store.cart.restaurant.name +
                 ". Start a new cart from " + restaurant.name + "?")) return;
    store.cart = { restaurant: null, lines: [] };
  }
  store.cart.restaurant = { id: restaurant.id, name: restaurant.name, address: restaurant.address };

  const line = store.cart.lines.find(l => l.menuItemId === item.id);
  if (line) line.quantity += 1;
  else store.cart.lines.push({ menuItemId: item.id, name: item.name, unitPrice: item.price, quantity: 1 });

  save(); paintChrome(); toast(item.name + " added");
}

function setQty(menuItemId, delta) {
  const line = store.cart.lines.find(l => l.menuItemId === menuItemId);
  if (!line) return;
  line.quantity += delta;
  if (line.quantity <= 0) store.cart.lines = store.cart.lines.filter(l => l.menuItemId !== menuItemId);
  if (!store.cart.lines.length) store.cart.restaurant = null;
  save(); paintChrome(); openCart();
}

/* ---------------- chrome ---------------- */

function paintChrome() {
  const n = cartCount();
  const badge = $("#cart-count");
  badge.textContent = n;
  badge.classList.toggle("hidden", n === 0);

  $("#who").innerHTML = store.user ? "Hello, <b>" + esc(store.user.fullName.split(" ")[0]) + "</b>" : "";
  $("#auth-btn").textContent = store.user ? "Sign out" : "Sign in";

  document.querySelectorAll("nav button").forEach(b =>
    b.classList.toggle("on", b.dataset.go === store.view));
}

function go(view, arg) {
  store.view = view;
  paintChrome();
  window.scrollTo({ top: 0 });
  ({ home: viewHome, restaurant: viewRestaurant, orders: viewOrders,
     checkout: viewCheckout, auth: viewAuth, admin: viewAdmin,
     rider: viewRider }[view] || viewHome)(arg);
}

/* ---------------- home ---------------- */

async function viewHome() {
  app().innerHTML = `
    <div class="view">
      <section class="hero">
        <div class="plate"></div>
        <h1>Good food, brought to your door.</h1>
      </section>
      <div class="searchbar">
        <input type="text" id="q" placeholder="Search a dish, cuisine or restaurant…"
               value="${esc(store.q)}" autocomplete="off">
      </div>
      <div class="filters" id="filters"></div>
      <div id="list"><p class="meta">Loading restaurants…</p></div>
    </div>`;

  $("#q").oninput = e => { store.q = e.target.value; paintList(); };

  await loadHome();
}

/**
 * Cuisine filtering is done by Restaurant Service (GET /restaurants/search?cuisineType=),
 * so the chips demonstrate a real API call rather than hiding rows in the browser.
 * The free-text box then narrows whatever came back, including by dish name - something
 * the backend has no endpoint for.
 */
async function loadHome() {
  const filtered = store.cuisine && store.cuisine !== "All";
  try {
    store.restaurants = await api(filtered
      ? "/restaurants/search?cuisineType=" + encodeURIComponent(store.cuisine)
      : "/restaurants/");
    if (!filtered) {
      store.cuisines = [...new Set(store.restaurants.map(r => r.cuisineType).filter(Boolean))].sort();
    }
  } catch (e) {
    $("#list").innerHTML = `<div class="empty"><h2>Can't load restaurants</h2><p>${esc(e.message)}</p></div>`;
    return;
  }
  paintFilters();
  paintList();
}

function paintFilters() {
  const box = $("#filters");
  if (!box) return;
  if (!store.cuisines.length) { box.innerHTML = ""; return; }
  box.innerHTML = ["All", ...store.cuisines].map(c =>
    `<button class="chip ${c === store.cuisine ? "on" : ""}" data-cuisine="${esc(c)}">${esc(c)}</button>`).join("");
}

/** Matches the restaurant name, its cuisine, or any dish name/category on its menu. */
function matchesQuery(r, q) {
  if ((r.name || "").toLowerCase().includes(q)) return true;
  if ((r.cuisineType || "").toLowerCase().includes(q)) return true;
  return (r.menuItems || []).some(i =>
    (i.name || "").toLowerCase().includes(q) || (i.category || "").toLowerCase().includes(q));
}

function paintList() {
  const q = store.q.trim().toLowerCase();
  const rows = q ? store.restaurants.filter(r => matchesQuery(r, q)) : store.restaurants;

  if (!rows.length) {
    $("#list").innerHTML = q || store.cuisine !== "All"
      ? `<div class="empty"><h2>Nothing matched</h2>
           <p>No restaurant serves “${esc(store.q || store.cuisine)}”. Try another search.</p></div>`
      : `<div class="empty"><h2>No restaurants yet</h2>
           <p>Open the <b>Admin</b> tab to add your first restaurant and its menu.</p></div>`;
    return;
  }

  $("#list").innerHTML = `<div class="grid">` + rows.map(r => {
    const items = (r.menuItems || []).length;
    return `<article class="card" data-open="${esc(r.id)}">
      <div class="thumb" style="background:${toneOf(r.id)}">${esc((r.name || "?").charAt(0))}</div>
      <div class="card-body">
        <h3>${esc(r.name)}</h3>
        <div class="meta">${esc(r.cuisineType || "Restaurant")} · ${esc(r.address || "")}</div>
        <div class="card-foot">
          <span class="pill ${r.available ? "open" : "shut"}">${r.available ? "Open" : "Closed"}</span>
          <span class="meta">${items} item${items === 1 ? "" : "s"}</span>
        </div>
      </div>
    </article>`;
  }).join("") + `</div>`;
}

/* ---------------- restaurant ---------------- */

async function viewRestaurant(id) {
  app().innerHTML = `<div class="view"><p class="meta">Loading…</p></div>`;
  let r;
  try { r = await api("/restaurants/" + id); }
  catch (e) { app().innerHTML = `<div class="empty"><h2>Not found</h2><p>${esc(e.message)}</p></div>`; return; }

  store.current = r;
  const items = r.menuItems || [];
  const cats = [...new Set(items.map(i => i.category || "Menu"))];

  app().innerHTML = `
    <div class="view">
      <button class="btn line small" data-go="home" style="margin-bottom:16px">← All restaurants</button>
      <div class="head">
        <div>
          <div class="eyebrow">${esc(r.cuisineType || "Restaurant")}</div>
          <h1>${esc(r.name)}</h1>
          <p class="lede">${esc(r.address || "")} ${r.phone ? "· " + esc(r.phone) : ""}</p>
        </div>
        <div class="grow"></div>
        <span class="pill ${r.available ? "open" : "shut"}">${r.available ? "Open now" : "Closed"}</span>
      </div>

      ${r.available ? "" : `<div class="note">This restaurant is closed right now, so ordering is disabled.</div>`}

      ${items.length ? cats.map(c => `
        <div class="menu-cat">${esc(c)}</div>
        ${items.filter(i => (i.category || "Menu") === c).map(i => `
          <div class="dish">
            <div class="dish-main">
              <h4>${esc(i.name)}</h4>
              <p>${esc(i.description || "")}</p>
            </div>
            <div class="price">${money(i.price)}</div>
            ${(i.available && r.available)
              ? `<button class="btn small" data-add="${esc(i.id)}">Add</button>`
              : `<span class="sold">unavailable</span>`}
          </div>`).join("")}
      `).join("") : `<div class="empty"><h2>No dishes yet</h2><p>This kitchen hasn't published a menu.</p></div>`}
    </div>`;
}

/* ---------------- cart drawer ---------------- */

function openCart() {
  const lines = store.cart.lines;
  $("#cart-layer").innerHTML = `
    <div class="scrim" data-close="1"></div>
    <aside class="drawer">
      <div class="drawer-head">
        <h2 style="margin:0">Your cart</h2><div class="grow"></div>
        <button class="x" data-close="1">×</button>
      </div>
      <div class="drawer-body">
        ${lines.length ? `
          <p class="meta" style="margin:12px 0 0">From <b>${esc(store.cart.restaurant.name)}</b></p>
          ${lines.map(l => `
            <div class="line">
              <div class="line-main">
                <h4>${esc(l.name)}</h4>
                <div class="meta">${money(l.unitPrice)} each</div>
              </div>
              <div class="qty">
                <button data-qty="${esc(l.menuItemId)}" data-d="-1">−</button>
                <span>${l.quantity}</span>
                <button data-qty="${esc(l.menuItemId)}" data-d="1">+</button>
              </div>
              <div class="price">${money(l.unitPrice * l.quantity)}</div>
            </div>`).join("")}
        ` : `<div class="empty"><h2>Cart is empty</h2><p>Add a dish to get started.</p></div>`}
      </div>
      ${lines.length ? `
        <div class="drawer-foot">
          <div class="total"><span>Total</span><b>${money(cartTotal())}</b></div>
          <button class="btn block" id="to-checkout">Checkout</button>
        </div>` : ""}
    </aside>`;
}

const closeCart = () => { $("#cart-layer").innerHTML = ""; };

/* ---------------- checkout ---------------- */

function viewCheckout() {
  if (!store.cart.lines.length) { go("home"); return; }
  if (!store.user) { toast("Please sign in to place an order"); go("auth"); return; }

  app().innerHTML = `
    <div class="view narrow">
      <h1 style="margin-bottom:4px">Checkout</h1>
      <p class="lede" style="margin-bottom:18px">From ${esc(store.cart.restaurant.name)}</p>
      <div class="panel">
        <div class="field">
          <label>Delivery address</label>
          <textarea id="addr" placeholder="House, road, area, city">House 12, Road 5, Dhanmondi, Dhaka</textarea>
        </div>
        <div class="field">
          <label>Pickup address (restaurant)</label>
          <input type="text" id="pickup" value="${esc(store.cart.restaurant.address || store.cart.restaurant.name)}">
        </div>

        <div style="border-top:1px solid var(--rule);margin:16px 0;padding-top:14px">
          ${store.cart.lines.map(l => `
            <div class="kv" style="justify-content:space-between">
              <span>${esc(l.name)} × ${l.quantity}</span>
              <b>${money(l.unitPrice * l.quantity)}</b>
            </div>`).join("")}
          <div class="total" style="margin-top:12px"><span>Total</span><b>${money(cartTotal())}</b></div>
        </div>

        <button class="btn block" id="place">Place order</button>
        <p class="meta" style="margin:11px 0 0;text-align:center">
          Payment is processed asynchronously over RabbitMQ once the order is placed.
        </p>
      </div>
    </div>`;
}

async function placeOrder() {
  const btn = $("#place");
  btn.disabled = true; btn.textContent = "Placing…";
  try {
    const order = await api("/orders/", {
      method: "POST",
      body: {
        customerId: store.user.id,
        restaurantId: store.cart.restaurant.id,
        restaurantName: store.cart.restaurant.name,
        productId: store.cart.lines[0].menuItemId,   // legacy single-item fields
        quantity: cartCount(),
        price: cartTotal(),
        items: store.cart.lines,
        deliveryAddress: $("#addr").value.trim(),
        pickupAddress: $("#pickup").value.trim()
      }
    });
    store.cart = { restaurant: null, lines: [] };
    save(); paintChrome();
    toast("Order placed — payment in progress");
    go("orders");
    watchOrder(order.id);
  } catch (e) {
    toast(e.message, true);
    btn.disabled = false; btn.textContent = "Place order";
  }
}

/* ---------------- orders ---------------- */

const STAGES = ["Placed", "Paid", "On the way", "Delivered"];

function stageIndex(order, delivery) {
  if (order.status === "CANCELLED" || order.status === "PAYMENT_FAILED") return -1;
  if (delivery && delivery.status === "DELIVERED") return 3;
  if (delivery && (delivery.status === "PICKED_UP" || delivery.status === "ASSIGNED")) return 2;
  if (order.status === "CONFIRMED") return 1;
  return 0;
}

async function viewOrders() {
  if (!store.user) { toast("Sign in to see your orders"); go("auth"); return; }
  app().innerHTML = `<div class="view"><h1>My orders</h1><div id="olist"><p class="meta">Loading…</p></div></div>`;

  try {
    store.orders = await api("/orders/customer/" + store.user.id);
  } catch (e) {
    $("#olist").innerHTML = `<div class="empty"><h2>Can't load orders</h2><p>${esc(e.message)}</p></div>`;
    return;
  }
  if (!store.orders.length) {
    $("#olist").innerHTML = `<div class="empty"><h2>No orders yet</h2>
      <p>When you place one, you'll be able to track it here.</p>
      <button class="btn" data-go="home" style="margin-top:14px">Browse restaurants</button></div>`;
    return;
  }
  paintOrders();
  store.orders.forEach(o => loadDetail(o.id));
}

function paintOrders() {
  $("#olist").innerHTML = store.orders.map(o => {
    const d = store.detail[o.id] || {};
    const delivery = d.delivery, payment = d.payment;
    const idx = stageIndex(o, delivery);
    const dead = idx === -1;

    const statusPill =
      o.status === "CANCELLED" ? `<span class="pill dead">Cancelled</span>` :
      o.status === "PAYMENT_FAILED" ? `<span class="pill dead">Payment failed</span>` :
      o.status === "PENDING_PAYMENT" ? `<span class="pill wait">Awaiting payment</span>` :
      delivery && delivery.status === "DELIVERED" ? `<span class="pill done">Delivered</span>` :
      `<span class="pill open">${esc(delivery ? delivery.status.replace("_", " ").toLowerCase() : "confirmed")}</span>`;

    const items = (o.items && o.items.length)
      ? o.items.map(i => esc(i.name) + " × " + i.quantity).join(", ")
      : (o.quantity + " × item");

    const canCancel = o.status !== "CANCELLED" && o.status !== "DELIVERED"
                      && !(delivery && (delivery.status === "PICKED_UP" || delivery.status === "DELIVERED"));
    const canRefund = payment && payment.status === "SUCCESS";

    return `<article class="order">
      <div class="order-top">
        <h3>${esc(o.restaurantName || "Order")}</h3>
        ${statusPill}
        <div class="grow"></div>
        <span class="ref">#${esc(o.id.slice(-8))}</span>
        <b>${money(o.price)}</b>
      </div>
      <div class="order-items">${items}</div>

      ${dead ? "" : `<div class="track">
        ${STAGES.map((s, i) => `<div class="stage ${i < idx ? "hit" : ""} ${i === idx ? "now" : ""}">
          <small>${s}</small></div>`).join("")}
      </div>`}

      ${o.cancelReason ? `<div class="kv"><span>Reason</span><b>${esc(o.cancelReason)}</b></div>` : ""}
      ${payment ? `<div class="kv"><span>Payment</span><b>${esc(payment.status)}</b>
          ${payment.transactionId ? `<span class="ref">${esc(payment.transactionId)}</span>` : ""}</div>` : ""}
      ${delivery ? `<div class="kv"><span>Delivery</span><b>${esc(delivery.status)}</b>
          ${delivery.riderId ? `<span class="meta">rider assigned</span>` : `<span class="meta">awaiting rider</span>`}</div>` : ""}
      <div class="kv"><span>To</span><b>${esc(o.deliveryAddress || "—")}</b></div>

      <div class="actions">
        <button class="btn line small" data-refresh="${esc(o.id)}">Refresh</button>
        ${canCancel ? `<button class="btn danger small" data-cancel="${esc(o.id)}">Cancel order</button>` : ""}
        ${canRefund ? `<button class="btn line small" data-refund="${esc(o.id)}">Request refund</button>` : ""}
      </div>
    </article>`;
  }).join("");
}

/* Payment and delivery live in their own services, so each is fetched separately.
   Both 404 legitimately while the async chain is still in flight. */
async function loadDetail(orderId) {
  const d = store.detail[orderId] || (store.detail[orderId] = {});
  await Promise.all([
    api("/payments/order/" + orderId).then(p => { d.payment = p; }).catch(() => { }),
    api("/api/deliveries/order/" + orderId).then(x => { d.delivery = x; }).catch(() => { })
  ]);
  if (store.view === "orders") paintOrders();
}

async function refreshOrder(orderId) {
  try {
    const fresh = await api("/orders/" + orderId);
    const i = store.orders.findIndex(o => o.id === orderId);
    if (i >= 0) store.orders[i] = fresh;
    await loadDetail(orderId);
  } catch (e) { toast(e.message, true); }
}

/* A new order moves PENDING_PAYMENT -> CONFIRMED -> delivery created -> rider
   assigned, all within a few seconds. Poll briefly so the customer sees it happen
   without refreshing.

   Delivery Service creates the delivery a moment before it assigns the rider, so
   stopping as soon as a delivery exists leaves the card stuck on "awaiting rider".
   Keep going until a rider is attached, or the order can't progress any further. */
function watchOrder(orderId, tries = 15) {
  let n = 0;
  const tick = async () => {
    n++;
    await refreshOrder(orderId);
    const d = store.detail[orderId] || {};
    const order = store.orders.find(o => o.id === orderId) || {};
    const delivery = d.delivery;

    const settled =
      (delivery && delivery.riderId) ||
      (delivery && ["DELIVERED", "CANCELLED"].includes(delivery.status)) ||
      ["PAYMENT_FAILED", "CANCELLED"].includes(order.status);

    if (!settled && n < tries) setTimeout(tick, 1200);
  };
  setTimeout(tick, 900);
}

async function cancelOrder(orderId) {
  const reason = prompt("Why are you cancelling? (optional)") || "";
  if (reason === null) return;
  try {
    await api("/orders/" + orderId + "/cancel", { method: "POST", body: { reason } });
    toast("Order cancelled");
    await refreshOrder(orderId);
    setTimeout(() => refreshOrder(orderId), 1500);   // let the refund land
  } catch (e) { toast(e.message, true); }
}

async function requestRefund(orderId) {
  if (!confirm("Request a refund for this order?")) return;
  try {
    const p = await api("/payments/order/" + orderId + "/refund",
      { method: "POST", body: { reason: "Refund requested by customer" } });
    toast("Refund processed — " + p.status);
    await refreshOrder(orderId);
  } catch (e) { toast(e.message, true); }
}

/* ---------------- auth ---------------- */

function viewAuth(mode) {
  const signup = mode === "signup";
  app().innerHTML = `
    <div class="view narrow">
      <h1 style="margin-bottom:4px">${signup ? "Create account" : "Sign in"}</h1>
      <p class="lede" style="margin-bottom:18px">
        ${signup ? "A few details and you're ready to order." : "Welcome back."}
      </p>
      <div class="panel">
        ${signup ? `
          <div class="field"><label>Full name</label><input type="text" id="name" placeholder="Ayesha Karim"></div>
          <div class="field"><label>Phone</label><input type="text" id="phone" placeholder="017xxxxxxxx"></div>` : ""}
        <div class="field"><label>Email</label><input type="email" id="email" placeholder="you@example.com"></div>
        <div class="field"><label>Password</label><input type="password" id="pass" placeholder="••••••••"></div>
        <button class="btn block" id="do-auth">${signup ? "Create account" : "Sign in"}</button>
        <p class="meta" style="text-align:center;margin:13px 0 0">
          ${signup ? "Already have an account?" : "New here?"}
          <a href="#" id="swap">${signup ? "Sign in" : "Create one"}</a>
        </p>
      </div>
    </div>`;

  $("#swap").onclick = e => { e.preventDefault(); viewAuth(signup ? "signin" : "signup"); };
  $("#do-auth").onclick = () => (signup ? doRegister() : doLogin());
}

async function doRegister() {
  const body = {
    fullName: $("#name").value.trim(),
    phone: $("#phone").value.trim(),
    email: $("#email").value.trim(),
    password: $("#pass").value
  };
  if (!body.fullName || !body.email || !body.password) return toast("Name, email and password are required", true);
  try {
    await api("/api/users/register", { method: "POST", body });
    toast("Account created — signing you in");
    await doLogin();
  } catch (e) { toast(e.message, true); }
}

async function doLogin() {
  const email = $("#email").value.trim(), password = $("#pass").value;
  if (!email || !password) return toast("Email and password are required", true);
  try {
    const res = await api("/api/users/login", { method: "POST", body: { email, password } });
    store.token = res.token || "";

    // Login returns the token plus a summary. Load the full profile with that token -
    // it is the authoritative user record, and it proves the JWT actually works before
    // the customer gets as far as checkout.
    try {
      store.user = await api("/api/users/profile", { auth: true });
    } catch (_) {
      store.user = { id: res.userId || res.id, fullName: res.fullName || email.split("@")[0], email };
    }
    save(); paintChrome();
    toast("Signed in");
    go(store.cart.lines.length ? "checkout" : "home");
  } catch (e) { toast(e.message, true); }
}

function signOut() {
  store.user = null; store.token = ""; save(); paintChrome();
  toast("Signed out"); go("home");
}

/* ---------------- admin ----------------
   Everything the system needs before a customer can order: restaurants, their
   menus, and riders for Delivery Service to assign. All typed by hand - there is
   no seed data. */

const isAdmin = () => !!(store.user && store.user.role === "ADMIN");

async function viewAdmin() {
  // The tab stays visible so an admin can always reach the sign-in form, but
  // the management screens only exist once the ADMIN role is proven.
  if (!isAdmin()) return viewAdminAuth();

  app().innerHTML = `
    <div class="view narrow">
      <h1 style="margin-bottom:4px">Admin</h1>
      <p class="lede" style="margin-bottom:18px">
        Signed in as <b>${esc(store.user.fullName)}</b> · Administrator
      </p>

      <div class="panel" style="margin-bottom:18px">
        <div class="head-row">
          <h3 style="margin:0">Restaurants</h3>
          <button class="btn small" id="show-new-restaurant">+ Add restaurant</button>
        </div>
        <div id="new-restaurant" hidden style="margin-bottom:14px">
          <div class="field"><label>Name</label><input id="r-name" placeholder="Pizza Palace"></div>
          <div class="field"><label>Address</label><input id="r-address" placeholder="Dhanmondi, Dhaka"></div>
          <div class="field"><label>Cuisine</label><input id="r-cuisine" placeholder="Italian"></div>
          <div class="field"><label>Phone</label><input id="r-phone" placeholder="0171234567"></div>
          <button class="btn block" id="do-restaurant">Create restaurant</button>
        </div>
        <div id="manage-list"></div>
      </div>

      <div class="panel">
        <div class="head-row">
          <h3 style="margin:0">Riders</h3>
          <button class="btn small" id="show-new-rider">+ Register rider</button>
        </div>
        <p class="meta" style="margin:0 0 12px">
          Delivery Service assigns one of these automatically when an order is confirmed.
        </p>
        <div id="new-rider" hidden style="margin-bottom:14px">
          <div class="field"><label>Name</label><input id="d-name" placeholder="Karim Uddin"></div>
          <div class="field"><label>Phone</label><input id="d-phone" placeholder="01712345678"></div>
          <div class="field"><label>Vehicle</label><input id="d-vehicle" placeholder="Motorbike"></div>
          <button class="btn block" id="do-rider">Register rider</button>
        </div>
        <div id="rider-list"></div>
      </div>
    </div>`;

  $("#show-new-restaurant").onclick = () => { $("#new-restaurant").hidden = !$("#new-restaurant").hidden; };
  $("#show-new-rider").onclick = () => { $("#new-rider").hidden = !$("#new-rider").hidden; };
  $("#do-restaurant").onclick = createRestaurant;
  $("#do-rider").onclick = registerRider;

  await Promise.all([loadRestaurants(), loadRiders()]);
}

/** Admin sign-in / sign-up, shown in place of the admin screens until the role checks out. */
function viewAdminAuth(mode) {
  const signup = mode === "signup";
  const signedInAsCustomer = store.user && !isAdmin();

  app().innerHTML = `
    <div class="view narrow">
      <h1 style="margin-bottom:4px">${signup ? "Create admin account" : "Admin sign in"}</h1>
      <p class="lede" style="margin-bottom:18px">
        ${signedInAsCustomer
          ? "You're signed in as a customer. Sign in with an administrator account to manage the system."
          : "Restaurants, menus and riders are managed by an administrator."}
      </p>
      <div class="panel">
        ${signup ? `
          <div class="field"><label>Full name</label><input type="text" id="a-name" placeholder="Admin"></div>
          <div class="field"><label>Phone</label><input type="text" id="a-phone" placeholder="017xxxxxxxx"></div>` : ""}
        <div class="field"><label>Email</label><input type="email" id="a-email" placeholder="admin@fds.test"></div>
        <div class="field"><label>Password</label><input type="password" id="a-pass" placeholder="••••••••"></div>
        <button class="btn block" id="do-admin-auth">${signup ? "Create admin account" : "Sign in"}</button>
        <p class="meta" style="text-align:center;margin:13px 0 0">
          ${signup ? "Already an admin?" : "No admin account yet?"}
          <a href="#" id="a-swap">${signup ? "Sign in" : "Create one"}</a>
        </p>
      </div>
    </div>`;

  $("#a-swap").onclick = e => { e.preventDefault(); viewAdminAuth(signup ? "signin" : "signup"); };
  $("#do-admin-auth").onclick = () => (signup ? registerAdmin() : signInAdmin());
}

async function registerAdmin() {
  const body = {
    fullName: $("#a-name").value.trim(),
    phone: $("#a-phone").value.trim(),
    email: $("#a-email").value.trim(),
    password: $("#a-pass").value,
    role: "ADMIN"
  };
  if (!body.fullName || !body.email || !body.password) return toast("Name, email and password are required", true);
  try {
    await api("/api/users/register", { method: "POST", body });
    toast("Admin account created — signing in");
    await signInAdmin();
  } catch (e) { toast(e.message, true); }
}

async function signInAdmin() {
  const email = $("#a-email").value.trim(), password = $("#a-pass").value;
  if (!email || !password) return toast("Email and password are required", true);
  try {
    const res = await api("/api/users/login", { method: "POST", body: { email, password } });
    if (res.role !== "ADMIN") return toast("That account is not an administrator", true);

    store.token = res.token || "";
    try {
      store.user = await api("/api/users/profile", { auth: true });
    } catch (_) {
      store.user = { id: res.userId, fullName: res.fullName || email.split("@")[0], email, role: res.role };
    }
    save(); paintChrome();
    toast("Signed in as administrator");
    go("admin");
  } catch (e) { toast(e.message, true); }
}

/** Single source of truth for the Admin tab: fetch once, repaint both sections. */
async function loadRestaurants() {
  try {
    store.restaurants = await api("/restaurants/");
  } catch (e) {
    store.restaurants = [];
    toast(e.message, true);
  }
  paintManage();
}

function paintManage() {
  const box = $("#manage-list");
  if (!box) return;

  if (!store.restaurants.length) {
    box.innerHTML = `<p class="meta">No restaurants yet — add one above.</p>`;
    return;
  }

  box.innerHTML = store.restaurants.map(r => {
    const items = r.menuItems || [];
    return `
      <div class="admin-block">
        <div class="row">
          <span><b>${esc(r.name)}</b>
            <span class="meta">${esc(r.cuisineType || "")}${r.address ? " · " + esc(r.address) : ""}</span>
          </span>
          <span class="admin-actions">
            <button class="btn small ${r.available ? "" : "line"}" data-rtoggle="${esc(r.id)}"
                    title="Toggle open/closed">${r.available ? "Open" : "Closed"}</button>
            <button class="btn small line" data-rmenu="${esc(r.id)}">Menu (${items.length})</button>
            <button class="btn small danger" data-rdel="${esc(r.id)}">Delete</button>
          </span>
        </div>
        <div class="menu-box" id="menu-${esc(r.id)}" hidden>
          ${items.length ? items.map(i => `
            <div class="row">
              <input type="text" id="mi-name-${esc(i.id)}" value="${esc(i.name || "")}" style="flex:2">
              <input type="number" id="mi-price-${esc(i.id)}" value="${i.price}" min="1" style="flex:1">
              <span class="admin-actions">
                <button class="btn small line" data-msave="${esc(r.id)}|${esc(i.id)}">Save</button>
                <button class="btn small ${i.available ? "" : "line"}"
                        data-mstock="${esc(r.id)}|${esc(i.id)}">${i.available ? "In stock" : "Out"}</button>
                <button class="btn small danger" data-mdel="${esc(r.id)}|${esc(i.id)}">×</button>
              </span>
            </div>`).join("") : `<p class="meta">No menu items yet.</p>`}
          <div class="row">
            <input type="text" id="ni-name-${esc(r.id)}" placeholder="New dish" style="flex:2">
            <input type="number" id="ni-price-${esc(r.id)}" placeholder="Price" min="1" style="flex:1">
            <span class="admin-actions">
              <button class="btn small" data-madd="${esc(r.id)}">Add dish</button>
            </span>
          </div>
        </div>
      </div>`;
  }).join("");
}

async function toggleRestaurant(id) {
  const r = store.restaurants.find(x => x.id === id);
  if (!r) return;
  try {
    await api(`/restaurants/${id}/availability?available=${!r.available}`, { method: "PATCH" });
    toast(r.name + (r.available ? " closed" : " opened"));
    await loadRestaurants();
  } catch (e) { toast(e.message, true); }
}

async function removeRestaurant(id) {
  const r = store.restaurants.find(x => x.id === id);
  if (!confirm(`Delete "${r ? r.name : id}" and its menu?`)) return;
  try {
    await api("/restaurants/" + id, { method: "DELETE" });
    toast("Deleted");
    await loadRestaurants();
  } catch (e) { toast(e.message, true); }
}

async function saveMenuItem(restaurantId, itemId) {
  const r = store.restaurants.find(x => x.id === restaurantId);
  const item = (r && (r.menuItems || []).find(i => i.id === itemId)) || {};
  const body = {
    name: $("#mi-name-" + itemId).value.trim(),
    price: Number($("#mi-price-" + itemId).value),
    category: item.category || "",
    description: item.description || "",
    available: item.available
  };
  if (!body.name || !(body.price > 0)) return toast("Name and a price above zero are required", true);
  try {
    await api(`/restaurants/${restaurantId}/menu/${itemId}`, { method: "PUT", body });
    toast("Saved " + body.name);
    await loadRestaurants();
  } catch (e) { toast(e.message, true); }
}

async function toggleStock(restaurantId, itemId) {
  const r = store.restaurants.find(x => x.id === restaurantId);
  const item = r && (r.menuItems || []).find(i => i.id === itemId);
  if (!item) return;
  try {
    await api(`/restaurants/${restaurantId}/menu/${itemId}/availability?available=${!item.available}`,
              { method: "PATCH" });
    toast(item.name + (item.available ? " is out of stock" : " is back in stock"));
    await loadRestaurants();
  } catch (e) { toast(e.message, true); }
}

async function removeMenuItem(restaurantId, itemId) {
  if (!confirm("Remove this dish?")) return;
  try {
    await api(`/restaurants/${restaurantId}/menu/${itemId}`, { method: "DELETE" });
    toast("Removed");
    await loadRestaurants();
  } catch (e) { toast(e.message, true); }
}

async function loadRiders() {
  const box = $("#rider-list");
  if (!box) return;
  try {
    const riders = await api("/api/riders/available");
    box.innerHTML = riders.length
      ? `<div class="meta">Available riders</div>` + riders.map(r =>
          `<div class="row"><span>${esc(r.name)} · ${esc(r.vehicleType || "")}</span>
           <span class="pill open">Available</span></div>`).join("")
      : `<p class="meta">No available riders yet.</p>`;
  } catch (e) {
    box.innerHTML = `<p class="meta">Can't reach Delivery Service — ${esc(e.message)}</p>`;
  }
}

async function createRestaurant() {
  const body = {
    name: $("#r-name").value.trim(),
    address: $("#r-address").value.trim(),
    cuisineType: $("#r-cuisine").value.trim(),
    phone: $("#r-phone").value.trim(),
    available: true
  };
  if (!body.name) return toast("Restaurant name is required", true);
  try {
    const created = await api("/restaurants/", { method: "POST", body });
    toast("Created " + created.name);
    ["#r-name", "#r-address", "#r-cuisine", "#r-phone"].forEach(s => ($(s).value = ""));
    await loadRestaurants();
  } catch (e) { toast(e.message, true); }
}

async function addMenuItem(restaurantId) {
  const body = {
    name: $("#ni-name-" + restaurantId).value.trim(),
    price: Number($("#ni-price-" + restaurantId).value),
    category: "",
    available: true
  };
  if (!body.name || !(body.price > 0)) return toast("Dish name and a price above zero are required", true);
  try {
    await api("/restaurants/" + restaurantId + "/menu", { method: "POST", body });
    toast("Added " + body.name);
    await loadRestaurants();
    const box = $("#menu-" + restaurantId);
    if (box) box.hidden = false;   // keep the menu open so the new dish is visible
  } catch (e) { toast(e.message, true); }
}

async function registerRider() {
  const body = {
    name: $("#d-name").value.trim(),
    phone: $("#d-phone").value.trim(),
    vehicleType: $("#d-vehicle").value.trim()
  };
  if (!body.name || !body.phone || !body.vehicleType) return toast("All rider fields are required", true);
  try {
    await api("/api/riders", { method: "POST", body });
    toast("Registered " + body.name);
    ["#d-name", "#d-phone", "#d-vehicle"].forEach(s => ($(s).value = ""));
    await loadRiders();
  } catch (e) { toast(e.message, true); }
}

/* ---------------- rider ----------------
   A rider does not have an account - they exist in Delivery Service and are
   registered by an Admin. This screen stands in for the rider's phone: pick who
   you are, then work the deliveries assigned to you. */

let riderId = "";

async function viewRider() {
  app().innerHTML = `
    <div class="view narrow">
      <h1 style="margin-bottom:4px">Rider</h1>
      <p class="lede" style="margin-bottom:18px">
        Deliveries are assigned to you automatically when an order is paid for.
      </p>
      <div class="panel">
        <div class="field"><label>You are</label><select id="rider-pick"></select></div>
        <div id="rider-jobs"><p class="meta">Loading…</p></div>
      </div>
    </div>`;

  const select = $("#rider-pick");
  let riders = [];
  try {
    riders = await api("/api/riders");
  } catch (e) {
    select.innerHTML = `<option value="">Can't reach Delivery Service</option>`;
    $("#rider-jobs").innerHTML = `<p class="meta">${esc(e.message)}</p>`;
    return;
  }

  if (!riders.length) {
    select.innerHTML = `<option value="">No riders yet</option>`;
    $("#rider-jobs").innerHTML = `<p class="meta">Register a rider from the Admin tab first.</p>`;
    return;
  }

  select.innerHTML = riders.map(r =>
    `<option value="${esc(r.id)}">${esc(r.name)} · ${r.available ? "available" : "on a delivery"}</option>`).join("");
  if (!riderId || !riders.some(r => r.id === riderId)) riderId = riders[0].id;
  select.value = riderId;
  select.onchange = () => { riderId = select.value; loadRiderJobs(); };

  await loadRiderJobs();
}

async function loadRiderJobs() {
  const box = $("#rider-jobs");
  if (!box || !riderId) return;

  let jobs = [];
  try {
    jobs = await api("/api/deliveries/rider/" + riderId);
  } catch (e) {
    box.innerHTML = `<p class="meta">${esc(e.message)}</p>`;
    return;
  }

  const done = j => j.status === "DELIVERED" || j.status === "CANCELLED";
  const live = jobs.filter(j => !done(j));
  const past = jobs.filter(done);

  box.innerHTML =
    (live.length ? live.map(j => `
      <div class="admin-block">
        <div class="row">
          <span><b>Order #${esc(String(j.orderId || "").slice(-6))}</b>
            <span class="meta">${esc(j.deliveryAddress || "")}</span></span>
          <span class="pill ${j.status === "PICKED_UP" ? "wait" : "flat"}">${esc(j.status)}</span>
        </div>
        <div class="row">
          <span class="meta">${j.status === "ASSIGNED"
            ? "Collect the food from the restaurant."
            : "On the way to the customer."}</span>
          <span class="admin-actions">
            ${j.status === "ASSIGNED"
              ? `<button class="btn small" data-dpick="${esc(j.id)}">Picked up</button>` : ""}
            ${j.status === "PICKED_UP"
              ? `<button class="btn small" data-ddone="${esc(j.id)}">Complete delivery</button>` : ""}
          </span>
        </div>
      </div>`).join("")
      : `<p class="meta">Nothing assigned right now. Place an order as a customer and it lands here.</p>`)
    + (past.length ? `<p class="meta" style="margin-top:14px">${past.length} completed earlier.</p>` : "");
}

async function riderPickUp(deliveryId) {
  try {
    await api("/api/deliveries/" + deliveryId + "/status", { method: "PUT", body: { status: "PICKED_UP" } });
    toast("Picked up — on your way");
    await loadRiderJobs();
  } catch (e) { toast(e.message, true); }
}

async function riderComplete(deliveryId) {
  try {
    await api("/api/deliveries/" + deliveryId + "/complete", { method: "POST" });
    toast("Delivered — you're available again");
    await viewRider();   // repaint the picker too, the rider is free now
  } catch (e) { toast(e.message, true); }
}

/* ---------------- events ---------------- */

document.addEventListener("click", e => {
  const t = e.target;
  const hit = sel => t.closest(sel);

  if (hit("[data-go]")) { go(hit("[data-go]").dataset.go); return; }
  if (hit("[data-open]")) { go("restaurant", hit("[data-open]").dataset.open); return; }
  if (hit("[data-add]")) {
    const id = hit("[data-add]").dataset.add;
    const item = (store.current.menuItems || []).find(i => i.id === id);
    if (item) addToCart(store.current, item);
    return;
  }
  if (hit("[data-close]")) { closeCart(); return; }
  if (hit("[data-qty]")) { const b = hit("[data-qty]"); setQty(b.dataset.qty, Number(b.dataset.d)); return; }
  if (t.id === "to-checkout") { closeCart(); go("checkout"); return; }
  if (t.id === "place") { placeOrder(); return; }
  if (hit("[data-rtoggle]")) { toggleRestaurant(hit("[data-rtoggle]").dataset.rtoggle); return; }
  if (hit("[data-rdel]")) { removeRestaurant(hit("[data-rdel]").dataset.rdel); return; }
  if (hit("[data-rmenu]")) {
    const box = $("#menu-" + hit("[data-rmenu]").dataset.rmenu);
    if (box) box.hidden = !box.hidden;
    return;
  }
  if (hit("[data-cuisine]")) { store.cuisine = hit("[data-cuisine]").dataset.cuisine; loadHome(); return; }
  if (hit("[data-dpick]")) { riderPickUp(hit("[data-dpick]").dataset.dpick); return; }
  if (hit("[data-ddone]")) { riderComplete(hit("[data-ddone]").dataset.ddone); return; }
  if (hit("[data-madd]")) { addMenuItem(hit("[data-madd]").dataset.madd); return; }
  if (hit("[data-msave]")) { const [r, i] = hit("[data-msave]").dataset.msave.split("|"); saveMenuItem(r, i); return; }
  if (hit("[data-mstock]")) { const [r, i] = hit("[data-mstock]").dataset.mstock.split("|"); toggleStock(r, i); return; }
  if (hit("[data-mdel]")) { const [r, i] = hit("[data-mdel]").dataset.mdel.split("|"); removeMenuItem(r, i); return; }
  if (hit("[data-refresh]")) { refreshOrder(hit("[data-refresh]").dataset.refresh); return; }
  if (hit("[data-cancel]")) { cancelOrder(hit("[data-cancel]").dataset.cancel); return; }
  if (hit("[data-refund]")) { requestRefund(hit("[data-refund]").dataset.refund); return; }
});

$("#cart-btn").onclick = openCart;
$("#auth-btn").onclick = () => (store.user ? signOut() : go("auth"));

/* ---------------- boot ---------------- */

paintChrome();
go("home");

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
  cuisine: "All",
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
    const msg = (data && (data.message || data.error)) || ("Request failed (" + res.status + ")");
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
     checkout: viewCheckout, auth: viewAuth }[view] || viewHome)(arg);
}

/* ---------------- home ---------------- */

async function viewHome() {
  app().innerHTML = `
    <div class="view">
      <section class="hero">
        <div class="plate"></div>
        <div class="eyebrow">Dhaka · delivering now</div>
        <h1>Good food, brought to your door.</h1>
        <p>Browse kitchens near you, build your order, and follow it from the
           restaurant to your doorstep in real time.</p>
      </section>
      <div class="filters" id="filters"></div>
      <div id="list"><p class="meta">Loading restaurants…</p></div>
    </div>`;

  try {
    store.restaurants = await api("/restaurants/");
  } catch (e) {
    $("#list").innerHTML = `<div class="empty"><h2>Can't load restaurants</h2><p>${esc(e.message)}</p></div>`;
    return;
  }
  paintFilters(); paintList();
}

function paintFilters() {
  const cuisines = ["All", ...new Set(store.restaurants.map(r => r.cuisineType).filter(Boolean))];
  $("#filters").innerHTML = cuisines.map(c =>
    `<button class="chip ${c === store.cuisine ? "on" : ""}" data-cuisine="${esc(c)}">${esc(c)}</button>`).join("");
}

function paintList() {
  const rows = store.cuisine === "All"
    ? store.restaurants
    : store.restaurants.filter(r => r.cuisineType === store.cuisine);

  if (!rows.length) {
    $("#list").innerHTML = `<div class="empty"><h2>Nothing here yet</h2>
      <p>No restaurants match. Add one from the developer console to get started.</p></div>`;
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

/* A new order moves PENDING_PAYMENT -> CONFIRMED -> delivery created within a
   second or two. Poll briefly so the customer sees it happen without refreshing. */
function watchOrder(orderId, tries = 12) {
  let n = 0;
  const tick = async () => {
    n++;
    await refreshOrder(orderId);
    const d = store.detail[orderId] || {};
    const done = d.delivery || (store.orders.find(o => o.id === orderId) || {}).status === "PAYMENT_FAILED";
    if (!done && n < tries) setTimeout(tick, 1200);
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

/* ---------------- events ---------------- */

document.addEventListener("click", e => {
  const t = e.target;
  const hit = sel => t.closest(sel);

  if (hit("[data-go]")) { go(hit("[data-go]").dataset.go); return; }
  if (hit("[data-cuisine]")) { store.cuisine = hit("[data-cuisine]").dataset.cuisine; paintFilters(); paintList(); return; }
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
  if (hit("[data-refresh]")) { refreshOrder(hit("[data-refresh]").dataset.refresh); return; }
  if (hit("[data-cancel]")) { cancelOrder(hit("[data-cancel]").dataset.cancel); return; }
  if (hit("[data-refund]")) { requestRefund(hit("[data-refund]").dataset.refund); return; }
});

$("#cart-btn").onclick = openCart;
$("#auth-btn").onclick = () => (store.user ? signOut() : go("auth"));

/* ---------------- boot ---------------- */

paintChrome();
go("home");

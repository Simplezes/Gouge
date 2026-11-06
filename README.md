<div align="center">

![Banner](https://cdn.modrinth.com/data/cached_images/dbfc26f8b33e5fb006fa133ef3d81ca115e5b7e0_0.webp)

### *Slam your pickaxe into a wall mid-fall and stop yourself before you splat.*

---

<a href="#">
  <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.3.1/assets/compact/supported/fabric_vector.svg" width="180">
</a>

<a href="#">
  <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.3.1/assets/compact/supported/forge_vector.svg" width="180">
</a>

<a href="#">
  <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.3.1/assets/compact/supported/neoforge_vector.svg" width="200">
</a>

<a href="https://github.com/Simplezes/Gouge">
  <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3.3.1/assets/compact/available/github_vector.svg" width="190">
</a>

</div>

## **Overview**

Falling toward a wall? **Right-click** it with a pickaxe. It bites into the surface and friction bleeds off your downward speed as you scrape along the edge. Harder blocks provide more resistance, slowing you down significantly faster. Once you've caught on, you don't have to stay put: hang on, slide, climb up, shimmy sideways, or kick off.

> **Note:** Higher-tier pickaxes hold onto surfaces longer and drain durability at a lower rate.

<div align="center">

| Block Type | Behavior | Effect |
| :--- | :--- | :--- |
| **Hard Blocks**<br>*(Stone, Obsidian)* | Friction eats momentum every tick until you stop dead, hanging in place until grip or durability expires. Hold a pickaxe in **both hands** to stretch that hang time (x1.5 by default), then hold **Space** to climb. Reach a ledge you can stand on and you mantle up onto it. | **Full Stop / Lock-In / Climbable** |
| **Soft Blocks**<br>*(Dirt, Sand)* | No locking mechanic. You slide continuously down the face of the wall. | **Reduced Damage** |

</div>

## **Moves**

| Move | How | What it does |
| :--- | :--- | :--- |
| **Climb** | Hang on a hard block with a pickaxe in each hand, then hold **Space** | Climb the wall and mantle onto ledges |
| **Shimmy** | Hang with a pickaxe in each hand, then hold **A** or **D** | Move sideways along the wall. Stops at corners and at the edge of the rock |
| **Launch** | Climb under a cap of 1 to 3 soft blocks (dirt, sand, grass) and keep holding **Space** | Push off and land on top of the cap |
| **Quick Escape** | Double-tap **Crouch** within 0.7 seconds | Kick off the wall at any time: hanging, climbing or sliding |

## **Enchantments**

Apply these to pickaxes with an Enchanting Table or an Anvil:

| Enchantment | Max Level | Effect |
| :--- | :-: | :--- |
| **Grip** | III | Hang on longer before slipping |
| **Momentum** | III | Shorter cooldown after slipping |
| **Spring** | III | Stronger launch over soft caps and stronger wall kicks |
| **Shimmy** | III | Faster sideways movement along walls |

## **Configuration**

Every value lives in `config/gouge.toml`, including a `[block_grip]` table to make specific blocks or tags easier or harder to hold. Every new feature can be switched on or off and tuned.

If you have **Cloth Config** installed (plus **Mod Menu** on Fabric), the main options also get an in-game screen. It is optional, and the mod runs the same without it.

---

## **Visual Showcase**

<div align="center">

### **Soft Block Dampening**

*Reduces fall damage roughly in half, capped so even a massive slide won't kill you.*

<table>
<tr><th>Standard Fall</th><th>Wall Slide</th></tr>
<tr><td align="center"><img src="https://cdn.modrinth.com/data/cached_images/dcd09984bdb60bb41392e8df04f49c6a7285083b.gif" width="200" height="375" alt="no slide, full fall damage"></td><td align="center"><img src="https://cdn.modrinth.com/data/cached_images/f20dd38c5c807ae3c23551352d61573a69514e21.gif" width="200" height="375" alt="sliding, half fall damage"></td></tr>
<tr><td align="center"><em>No slide (Full Damage)</em></td><td align="center"><em>Sliding (Half Damage)</em></td></tr>
</table>

<br>

### **Hard Block Grip**

*Lock on, climb up with two pickaxes, or kick off whenever you want.*

<table>
<tr><th>Hard Block Catch</th><th>Wall Climbing</th><th>Wall Kick Jump</th></tr>
<tr><td align="center"><img src="https://cdn.modrinth.com/data/cached_images/3cca266c0593abf7118703cc690cfe73d8471442.gif" width="200" height="375" alt="locking onto a hard block"></td><td align="center"><img src="https://cdn.modrinth.com/data/cached_images/c0ddae37218eef386ffebeb7407bd95239e4a13e.gif" width="299" height="375" alt="climbing a wall with dual pickaxes"></td><td align="center"><img src="https://cdn.modrinth.com/data/cached_images/f2fb84856418cc4ea361fb6f0b2393acf008eb59.gif" width="200" height="375" alt="wall kick"></td></tr>
<tr><td align="center"><em>Bleeds speed until fully locked</em></td><td align="center"><em>Hold Space with two pickaxes to climb and mantle</em></td><td align="center"><em>Double-tap crouch to kick off mid-hang</em></td></tr>
</table>

<br>

### **Soft Launch & Lateral Travel**

*Spring over a soft cap on top of the rock, or shimmy sideways along the wall.*

<table>
<tr><th>Soft Launch</th><th>Lateral Travel</th></tr>
<tr><td align="center"><img src="https://cdn.modrinth.com/data/cached_images/41398abb5993dfb864a4317508a99e7b46cf8c34.gif" width="260" alt="launching over a soft block cap"></td><td align="center"><img src="https://cdn.modrinth.com/data/cached_images/2533e84552cc0edd974ca188308de2195766b940.gif" width="260" alt="shimmying sideways along a wall"></td></tr>
<tr><td align="center"><em>Keep holding Space against the cap to push off and land on top</em></td><td align="center"><em>Hold A or D with two pickaxes to move along the wall</em></td></tr>
</table>

</div>

---

## **Compatibility**

Tested and confirmed to work seamlessly alongside:

* **Tinkers' Construct** *(Credit: Elizabeth_Mahiko)*
* **Mekanism / Mekanism Tools**
* **Silent Gear**
* **ParCool** *(Credit: Brizo)* – 3.x and 4.x (4.x on MC 1.21.1 / 1.20.1)
* **Zipline: Rezipped** *(Credit: greetedsnow)*
* **Wall-Jump TXF** *(credit: ANormalRaft)*

---

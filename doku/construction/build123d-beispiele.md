# build123d – Konstruktions-Beispiele & Workflow

> Praxisnahe Beispiele für häufige Konstruktionsaufgaben.
> Alle Beispiele in **Algebra Mode**, alle Maße in **mm**.

---

## Beispiel 1: Einfaches Gehäuse mit Deckel

### Prompt-Beispiel
> „Erstelle ein rechteckiges Gehäuse 80×50×30mm mit 2mm Wandstärke, abgerundeten Kanten (R3), 4 Schraubdome M3 in den Ecken und einen passenden Deckel."

### Code

```python
from build123d import *

# ============================================================
# Parameter
# ============================================================
length, width, height = 80, 50, 30
wall = 2.0
corner_r = 3.0
screw_r = 1.5         # M3 Kernloch
dome_r = 5.0           # Schraubdom Außenradius
dome_h = height - wall # Domhöhe
lid_thick = 2.0
lip_h = 3.0            # Deckelfeder-Höhe
clearance = 0.2        # Spielpassung

# ============================================================
# Gehäuse-Unterteil
# ============================================================
# Grundkörper
case = Box(length, width, height,
           align=(Align.CENTER, Align.CENTER, Align.MIN))
case = fillet(case.edges().filter_by(Axis.Z), corner_r)

# Aushöhlen (Shell)
top_face = case.faces().sort_by(Axis.Z).last
case = offset(case, -wall, openings=top_face)

# Schraubdome in den Ecken
dx = length / 2 - dome_r - wall
dy = width / 2 - dome_r - wall
for x, y in [(dx, dy), (-dx, dy), (-dx, -dy), (dx, -dy)]:
    dome = Pos(x, y) * Cylinder(dome_r, dome_h,
               align=(Align.CENTER, Align.CENTER, Align.MIN))
    hole = Pos(x, y) * Cylinder(screw_r, dome_h,
               align=(Align.CENTER, Align.CENTER, Align.MIN))
    case = case + dome - hole

# ============================================================
# Deckel
# ============================================================
lid = Box(length, width, lid_thick,
          align=(Align.CENTER, Align.CENTER, Align.MIN))
lid = fillet(lid.edges().filter_by(Axis.Z), corner_r)

# Innensteg (Feder)
inner_lip = Box(length - 2*wall - clearance,
                width - 2*wall - clearance, lip_h,
                align=(Align.CENTER, Align.CENTER, Align.MIN))
inner_lip -= Box(length - 4*wall,
                 width - 4*wall, lip_h,
                 align=(Align.CENTER, Align.CENTER, Align.MIN))
lid += Pos(Z=-lip_h) * inner_lip

# Schraubenlöcher im Deckel
for x, y in [(dx, dy), (-dx, dy), (-dx, -dy), (dx, -dy)]:
    lid -= Pos(x, y) * Cylinder(screw_r + 0.2, lid_thick + lip_h,
               align=(Align.CENTER, Align.CENTER, Align.MIN))

# ============================================================
# Export
# ============================================================
export_step(case, "gehaeuse_unterteil.step")
export_step(lid, "gehaeuse_deckel.step")
export_stl(case, "gehaeuse_unterteil.stl")
export_stl(lid, "gehaeuse_deckel.stl")
```

---

## Beispiel 2: Halterung / Bracket

### Prompt-Beispiel
> „L-förmige Wandhalterung mit Montagelöchern, Verrundung in der Ecke und Versteifungsrippe."

```python
from build123d import *

# Parameter
plate_w, plate_h, plate_t = 40, 60, 4
flange_w, flange_d = 40, 30
fillet_r = 8
hole_d = 5.0
rib_t = 3

# Vertikale Platte
vert = Box(plate_w, plate_t, plate_h,
           align=(Align.CENTER, Align.MIN, Align.MIN))

# Horizontale Platte (Flansch)
horiz = Box(plate_w, flange_d, plate_t,
            align=(Align.CENTER, Align.MIN, Align.MIN))

# Vereinigen
bracket = vert + horiz

# Verrundung in der Innenecke
inner_edges = bracket.edges().filter_by(Axis.X).filter_by(
    lambda e: e.center().Y > plate_t/2 and e.center().Z > plate_t/2
)
if len(inner_edges) > 0:
    bracket = fillet(inner_edges, fillet_r)

# Versteifungsrippe (Dreieck)
rib_pts = [(0, plate_t, plate_t),
           (0, plate_t, plate_h * 0.6),
           (0, flange_d * 0.7, plate_t)]
rib_sketch = Plane.YZ * Polygon(*[(p[1], p[2]) for p in rib_pts])
rib = extrude(rib_sketch, rib_t, both=True)
bracket += rib

# Montagelöcher (vertikal, Wandmontage)
for z_pos in [15, 45]:
    bracket -= Pos(0, plate_t/2, z_pos) * Rot(X=90) * Cylinder(hole_d/2, plate_t*2)

# Montagelöcher (horizontal, Befestigung)
for y_pos in [10, 22]:
    bracket -= Pos(0, y_pos, plate_t/2) * Cylinder(hole_d/2, plate_t*2)

export_step(bracket, "halterung.step")
```

---

## Beispiel 3: Drehkörper (Vase / Gefäß)

### Prompt-Beispiel
> „Eine Vase mit geschwungenem Profil, 80mm hoch, Öffnung 40mm, Boden 25mm."

```python
from build123d import *

# Parameter
total_h = 80
top_r = 20       # Öffnungsradius
bottom_r = 12.5  # Bodenradius
waist_r = 10     # Engste Stelle
waist_h = 30     # Höhe der Taille
wall = 2.0

# Außenprofil (Spline)
outer_pts = [
    (bottom_r, 0),
    (waist_r, waist_h),
    (top_r + 5, total_h * 0.75),
    (top_r, total_h),
]

# Innenprofil
inner_pts = [
    (bottom_r - wall, wall),  # Boden-Innenseite
    (waist_r - wall, waist_h),
    (top_r + 5 - wall, total_h * 0.75),
    (top_r - wall, total_h + 1),  # Etwas höher für sauberen Schnitt
]

# Außenprofil als geschlossene Fläche
outer_lines = Curve() + [
    Line((0, 0), (bottom_r, 0)),
    Spline(*outer_pts),
    Line((top_r, total_h), (0, total_h)),
    Line((0, total_h), (0, 0)),
]
outer_face = make_face(outer_lines)

# Innenprofil
inner_lines = Curve() + [
    Line((0, wall), (bottom_r - wall, wall)),
    Spline(*inner_pts),
    Line((top_r - wall, total_h + 1), (0, total_h + 1)),
    Line((0, total_h + 1), (0, wall)),
]
inner_face = make_face(inner_lines)

# Profil = Außen minus Innen
profile = Plane.XZ * (outer_face - inner_face)

# Revolve um Z-Achse
vase = revolve(profile, axis=Axis.Z)

export_step(vase, "vase.step")
export_stl(vase, "vase.stl")
```

---

## Beispiel 4: Zahnrad (Vereinfacht)

### Prompt-Beispiel
> „Stirnzahnrad mit 20 Zähnen, Modul 2, 10mm breit, Bohrung 8mm."

```python
from build123d import *
import math

# Parameter
module = 2.0
teeth = 20
width = 10.0
bore_d = 8.0
pressure_angle = 20  # Grad

# Berechnete Werte
pitch_r = module * teeth / 2
addendum = module
dedendum = 1.25 * module
outer_r = pitch_r + addendum
root_r = pitch_r - dedendum
tooth_angle = 360 / teeth

# Vereinfachtes Zahnprofil (Trapez-Approximation)
tooth_top_half_angle = tooth_angle * 0.2  # Zahnkopf-Breite
tooth_base_half_angle = tooth_angle * 0.3  # Zahnfuß-Breite

# Einen Zahn als Polygon konstruieren
def tooth_profile(angle_offset=0):
    pts = []
    for a_deg in [
        angle_offset - tooth_base_half_angle,
        angle_offset - tooth_top_half_angle,
        angle_offset + tooth_top_half_angle,
        angle_offset + tooth_base_half_angle,
    ]:
        a_rad = math.radians(a_deg)
        r = outer_r if abs(a_deg - angle_offset) <= tooth_top_half_angle else root_r
        pts.append((r * math.cos(a_rad), r * math.sin(a_rad)))
    return pts

# Zahnrad-Sketch: Basis-Kreis + Zähne
gear_sketch = Circle(root_r)
for i in range(teeth):
    angle = i * tooth_angle
    pts = tooth_profile(angle)
    tooth = Polygon(*pts)
    gear_sketch += tooth

# Bohrung
gear_sketch -= Circle(bore_d / 2)

# Extrudieren
gear = extrude(gear_sketch, width)

export_step(gear, "zahnrad.step")
```

---

## Beispiel 5: Rohr-Verbinder (T-Stück)

### Prompt-Beispiel
> „T-Stück für 20mm Außendurchmesser Rohr, Wandstärke 2mm."

```python
from build123d import *

# Parameter
outer_d = 20
wall = 2.0
inner_d = outer_d - 2 * wall
main_length = 60
branch_length = 30

# Hauptrohr (horizontal)
main_outer = Cylinder(outer_d/2, main_length)
main_inner = Cylinder(inner_d/2, main_length)
main_tube = main_outer - main_inner

# Abzweig (vertikal nach oben)
branch_outer = Pos(Z=0) * Cylinder(outer_d/2, branch_length,
                align=(Align.CENTER, Align.CENTER, Align.MIN))
branch_inner = Pos(Z=0) * Cylinder(inner_d/2, branch_length + wall,
                align=(Align.CENTER, Align.CENTER, Align.MIN))

# Vereinigung mit Innenraum-Subtraktion
t_piece = main_outer + branch_outer
t_piece -= main_inner
t_piece -= branch_inner

# Verrundung am Übergang
transition_edges = t_piece.edges().filter_by(
    lambda e: abs(e.center().Z) < 1 and e.length < outer_d
)
if len(transition_edges) > 0:
    t_piece = fillet(transition_edges, wall)

export_step(t_piece, "t_stueck.step")
```

---

## Beispiel 6: Handy-Halter

### Prompt-Beispiel
> „Einfacher Smartphone-Ständer für den Schreibtisch, Neigung 70°, für Handys bis 12mm dick."

```python
from build123d import *
import math

# Parameter
phone_thick = 12.0
angle = 70          # Neigung in Grad
base_length = 60
base_width = 80
base_thick = 5
back_height = 80
back_thick = 4
lip_height = 15     # Untere Lippe
slot_depth = phone_thick + 2  # Etwas Spiel

# Basis-Platte
base = Box(base_width, base_length, base_thick,
           align=(Align.CENTER, Align.CENTER, Align.MIN))

# Rückwand (angewinkelt)
back = Box(base_width, back_thick, back_height,
           align=(Align.CENTER, Align.MIN, Align.MIN))
# Rotation um die Unterkante
back = Pos(0, -base_length/2 + back_thick, base_thick) * Rot(X=angle-90) * back

# Lippe vorne
lip = Box(base_width, lip_height, base_thick + 3,
          align=(Align.CENTER, Align.MIN, Align.MIN))
lip_x_offset = -base_length/2 + lip_height * math.cos(math.radians(90-angle))
lip = Pos(0, lip_x_offset, 0) * lip

# Zusammensetzen
stand = base + back + lip

# Slot für das Handy
slot = Box(base_width - 10, slot_depth, back_height,
           align=(Align.CENTER, Align.CENTER, Align.MIN))
slot_pos = Pos(0, lip_x_offset + lip_height/2, base_thick)
stand -= slot_pos * Rot(X=angle-90) * slot

# Verrundungen
stand = fillet(stand.edges().filter_by(Axis.X).filter_by(
    lambda e: e.length > 3), 2)

export_step(stand, "handy_halter.step")
```

---

## Workflow-Tipps

### 1. Schrittweise konstruieren
```python
# Schritt für Schritt entwickeln, nach jedem Schritt validieren
part = Box(10, 20, 5)
# show(part)  # Zwischenkontrolle

part = fillet(part.edges().filter_by(Axis.Z), 1)
# show(part)  # Nächste Kontrolle

part -= Cylinder(2, 5)
# show(part)  # Fertig
```

### 2. Fehler vermeiden
- **Fillet-Radius nie größer als halbe Kantenlänge**
- **Shell: Wandstärke nie größer als halbe kleinste Dimension**
- **Boolesche Operationen: Objekte müssen sich überlappen (Fuse) oder schneiden (Cut)**
- **Revolve: Profil darf die Drehachse nicht kreuzen**

### 3. Debugging
```python
# Bounding Box prüfen
print(f"Größe: {part.bounding_box().size}")
print(f"Volumen: {part.volume:.1f} mm³")
print(f"Anzahl Faces: {len(part.faces())}")
print(f"Anzahl Edges: {len(part.edges())}")
```

### 4. Performance
```python
# SCHLECHT (langsam):
result = obj1 + obj2 + obj3 + obj4

# GUT (schnell, vektorisiert):
result = obj1 + [obj2, obj3, obj4]

# SCHLECHT:
for loc in locations:
    part -= loc * hole

# GUT:
part -= [loc * hole for loc in locations]
```

---

## Referenz-Projekte & Ressourcen

| Ressource | URL |
|---|---|
| build123d Doku | https://build123d.readthedocs.io |
| build123d GitHub | https://github.com/gumyr/build123d |
| VS Code Viewer | https://github.com/bernhard-42/vscode-ocp-cad-viewer |
| CadQuery (Alternative) | https://cadquery.readthedocs.io |
| Text-to-FreeCAD (RAG) | https://github.com/giuliano-t/openAI-to-freeCAD-workflow |
| 3D_Gen (RAG+LLM→STL) | https://github.com/lumlime/3D_Gen |

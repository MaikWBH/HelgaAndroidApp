# build123d – API-Kurzreferenz (Algebra Mode)

> Kompaktes Nachschlagewerk für die KI-gestützte Konstruktion mit build123d.
> Alle Beispiele in **Algebra Mode** (kein Builder Mode).

---

## Installation

```bash
pip install build123d
# Viewer für VS Code:
pip install ocp-vscode
```

---

## 1. 3D-Grundkörper (Part Objects)

| Objekt | Signatur | Beschreibung |
|---|---|---|
| `Box` | `Box(length, width, height)` | Quader |
| `Cylinder` | `Cylinder(radius, height)` | Zylinder |
| `Sphere` | `Sphere(radius)` | Kugel |
| `Cone` | `Cone(bottom_radius, top_radius, height)` | Kegel/Kegelstumpf |
| `Torus` | `Torus(major_radius, minor_radius)` | Torus |
| `Wedge` | `Wedge(xsize, ysize, zsize, xmin, zmin, xmax, zmax)` | Keil |

Alle haben optionale Parameter: `rotation=(rx,ry,rz)`, `align=(Align.X, Align.Y, Align.Z)`, `mode=Mode.ADD`

### Align-Werte
- `Align.MIN` – Objekt beginnt am Ursprung
- `Align.CENTER` – Objekt ist zentriert (Standard)
- `Align.MAX` – Objekt endet am Ursprung

```python
# Box mit Unterkante auf Z=0
box = Box(10, 20, 5, align=(Align.CENTER, Align.CENTER, Align.MIN))
```

---

## 2. 2D-Sketch-Objekte

| Objekt | Signatur | Beschreibung |
|---|---|---|
| `Circle` | `Circle(radius)` | Kreis |
| `Rectangle` | `Rectangle(width, height)` | Rechteck |
| `RectangleRounded` | `RectangleRounded(width, height, radius)` | Rechteck mit Rundung |
| `RegularPolygon` | `RegularPolygon(radius, side_count)` | Regelmäßiges Polygon |
| `Polygon` | `Polygon(*pts)` | Polygon aus Punkten |
| `Ellipse` | `Ellipse(x_radius, y_radius)` | Ellipse |
| `Trapezoid` | `Trapezoid(width, height, left_angle)` | Trapez |
| `Triangle` | `Triangle(a=..., b=..., C=...)` | Dreieck (Seiten/Winkel) |
| `SlotOverall` | `SlotOverall(width, height)` | Langloch |
| `SlotCenterToCenter` | `SlotCenterToCenter(separation, height)` | Langloch (Mitte-Mitte) |
| `Text` | `Text("text", font_size)` | Text als 2D-Sketch |

---

## 3. 1D-Linien-Objekte (Curves)

| Objekt | Signatur | Beschreibung |
|---|---|---|
| `Line` | `Line(pt1, pt2)` | Gerade Linie |
| `Polyline` | `Polyline(*pts, close=False)` | Linienzug |
| `Spline` | `Spline(*pts)` | Spline durch Punkte |
| `Bezier` | `Bezier(*pts, weights=None)` | Bézierkurve |
| `CenterArc` | `CenterArc(center, radius, start_angle, arc_size)` | Kreisbogen (Mitte) |
| `ThreePointArc` | `ThreePointArc(pt1, pt2, pt3)` | Kreisbogen (3 Punkte) |
| `RadiusArc` | `RadiusArc(start, end, radius)` | Kreisbogen (Radius) |
| `JernArc` | `JernArc(start, tangent, radius, arc_size)` | Tangentialer Bogen |
| `TangentArc` | `TangentArc(pt1, pt2, tangent=...)` | Tangentialer Bogen |
| `Helix` | `Helix(pitch, height, radius)` | Helix/Wendel |
| `PolarLine` | `PolarLine(start, length, angle=...)` | Polare Linie |
| `FilletPolyline` | `FilletPolyline(*pts, radius=r)` | Linienzug mit Verrundung |

### Linie-Operatoren
```python
edge @ 0.5    # Position bei 50% der Länge → Vector
edge % 0.5    # Tangente bei 50% → Vector
edge ^ 0.5    # Location bei 50%
```

---

## 4. Boolesche Operationen

```python
a + b         # Vereinigung (Fuse)
a - b         # Subtraktion (Cut)
a & b         # Schnittmenge (Intersect)

a += b        # In-Place Vereinigung
a -= b        # In-Place Subtraktion

# Vektorisiert (effizienter):
a - [b1, b2, b3]     # Mehrere auf einmal subtrahieren
a + [b1, b2, b3]     # Mehrere auf einmal vereinigen
```

---

## 5. Positionierung & Transformation

```python
Pos(x, y, z)          # Translation (alle Achsen)
Pos(X=5)              # Translation (nur X)
Pos(Y=10, Z=3)        # Translation (Y und Z)
Rot(rx, ry, rz)       # Rotation um Achsen (Grad)
Rot(X=45)             # Rotation nur um X
```

### Ebenen (Planes)
```python
Plane.XY              # Standard-Ebene (Z nach oben)
Plane.XZ              # Seitenebene (Y nach oben)
Plane.YZ              # Frontalebene (X nach oben)
Plane.XY.offset(10)   # XY-Ebene um 10mm nach oben verschoben
Plane(face)           # Ebene aus einer Fläche erstellen
```

### Kombination
```python
# Auf Ebene platzieren
Plane.XZ * Circle(5)

# Auf Ebene platzieren + verschieben
Plane.XZ * Pos(10, 5) * Circle(5)

# Rotieren und verschieben
Pos(10, 0, 5) * Rot(Z=45) * Box(5, 5, 5)
```

---

## 6. Operationen

### Extrude
```python
extrude(sketch, amount=10)                # Normal extrudieren
extrude(sketch, amount=10, both=True)     # Beidseitig
extrude(sketch, until=Until.NEXT, target=part)  # Bis zur nächsten Fläche
```

### Revolve (Drehen)
```python
revolve(profile, axis=Axis.Z)                        # 360° um Z
revolve(profile, axis=Axis.Z, revolution_arc=180)     # 180° um Z
```

### Sweep (Entlang Pfad)
```python
sweep(profile, path=wire)
sweep(profile, path=wire, multisection=True)  # Mehrere Profile
```

### Loft (Zwischen Profilen)
```python
faces = Sketch() + [
    Circle(r1),
    Plane.XY.offset(h) * Rectangle(w, w),
]
result = loft(faces)
```

### Fillet (Verrundung)
```python
fillet(part.edges(), radius=2)                        # Alle Kanten
fillet(part.edges().filter_by(Axis.Z), radius=2)      # Nur vertikale
fillet(part.edges().group_by(Axis.Z)[-1], radius=2)   # Nur obere
```

### Chamfer (Fase)
```python
chamfer(edges, length=1)              # Symmetrische Fase
chamfer(edges, length=1, length2=2)   # Asymmetrische Fase
```

### Offset / Shell (Aushöhlung)
```python
# 3D Shell
top = part.faces().sort_by(Axis.Z).last
result = offset(part, amount=-wall, openings=top)

# 2D Offset
result = offset(sketch, amount=5)
```

### Mirror (Spiegeln)
```python
result = mirror(part, about=Plane.YZ)
result += mirror(result, about=Plane.XZ)
```

### Split (Teilen)
```python
result = split(part, bisect_by=Plane.YZ)
```

### Make Face (Fläche aus Linien)
```python
face = make_face([line1, line2, line3, line4])
face = make_face(wire)
```

---

## 7. Löcher & Befestigungen

```python
# Durchgangsloch
part -= Hole(radius=2.5, depth=height)

# Sackloch
part -= Pos(x, y) * Hole(radius=2.5, depth=10)

# Senkbohrung
part -= CounterBoreHole(radius=2.5, counter_bore_radius=5, counter_bore_depth=3, depth=20)

# Senkloch (Kegelsenker)
part -= CounterSinkHole(radius=2.5, counter_sink_radius=5, depth=20)
```

---

## 8. Selektoren (Edges, Faces, Vertices auswählen)

### ShapeList-Methoden
```python
part.edges()                          # Alle Kanten
part.faces()                          # Alle Flächen
part.vertices()                       # Alle Ecken

# Sortieren
.sort_by(Axis.Z)                      # Nach Z sortieren
.sort_by(Axis.Z).last                 # Höchste
.sort_by(Axis.Z).first                # Niedrigste

# Gruppieren
.group_by(Axis.Z)                     # Nach Z-Position gruppieren
.group_by(Axis.Z)[-1]                 # Gruppe mit höchstem Z
.group_by(Axis.Z)[0]                  # Gruppe mit niedrigstem Z

# Filtern
.filter_by(Axis.Z)                    # Parallel zu Z
.filter_by(Axis.X)                    # Parallel zu X
.filter_by(GeomType.CIRCLE)           # Kreisförmig
.filter_by(GeomType.LINE)             # Gerade Linien
.filter_by(GeomType.CYLINDER)         # Zylindrische Flächen
.filter_by(lambda e: e.length > 5)    # Custom Filter
```

### Operatoren für ShapeLists
```python
edges > Axis.Z          # sort_by(Axis.Z) aufsteigend
edges < Axis.Z          # sort_by(Axis.Z) absteigend
edges >> Axis.Z         # group_by(Axis.Z)[-1]  (höchste Gruppe)
edges << Axis.Z         # group_by(Axis.Z)[0]   (niedrigste Gruppe)
edges | Axis.Z          # filter_by(Axis.Z)
edges | GeomType.CIRCLE # filter_by(GeomType.CIRCLE)
```

---

## 9. Locations (Wiederholungsmuster)

```python
# Gitter-Muster
GridLocations(x_spacing, y_spacing, x_count, y_count)

# Polar-Muster
PolarLocations(radius, count, start_angle=0, angular_range=360)

# Hex-Muster
HexLocations(apothem, x_count, y_count)

# Manuelle Positionen
Locations((x1,y1), (x2,y2), (x3,y3))
```

### Anwendung
```python
# Löcher im Kreis
holes = PolarLocations(30, 6) * Circle(3)
part -= extrude(plane * holes, -depth)

# Löcher im Gitter
holes = [loc * Circle(2) for loc in GridLocations(10, 10, 3, 3)]
part -= extrude(plane * (Sketch() + holes), -depth)
```

---

## 10. Import / Export

```python
# STEP (verlustfrei, bevorzugt)
export_step(part, "output.step")
imported = import_step("input.step")

# STL (für 3D-Druck)
export_stl(part, "output.stl")
export_stl(part, "output.stl", angular_tolerance=0.05, tolerance=0.01)

# SVG (2D)
export_svg(sketch, "output.svg")

# DXF (für Laserschnitt)
# export_dxf(sketch, "output.dxf")  # Ab neueren Versionen
```

---

## 11. Nützliche Geometrie-Klassen

```python
Vector(x, y, z)        # 3D-Vektor
Axis(origin, direction) # Achse
Axis.X, Axis.Y, Axis.Z # Standard-Achsen

# Eigene Achse
my_axis = Axis((0, 0, 0), (1, 1, 0))
```

---

## 12. Häufige Metrische Maße

### Schrauben (ISO)
| Gewinde | Kernloch-Ø | Durchgangs-Ø | Kopf-Ø (Senk) |
|---|---|---|---|
| M2 | 1.6 mm | 2.4 mm | 4.0 mm |
| M2.5 | 2.05 mm | 2.9 mm | 5.0 mm |
| M3 | 2.5 mm | 3.4 mm | 6.0 mm |
| M4 | 3.3 mm | 4.5 mm | 8.0 mm |
| M5 | 4.2 mm | 5.5 mm | 10.0 mm |
| M6 | 5.0 mm | 6.6 mm | 12.0 mm |
| M8 | 6.8 mm | 9.0 mm | 16.0 mm |

### Heat-Set Inserts (M3, typisch)
- Einpressloch: Ø 4.0 mm
- Tiefe: 5.0 mm
- Einfädel-Fase: 0.5 mm × 45°

### Standardmaße 3D-Druck
- Nozzle: 0.4 mm → min. Feature: 0.4 mm
- Layer: 0.2 mm → min. Z-Feature: 0.2 mm
- Wandstärke: ≥ 1.2 mm (3 Perimeter × 0.4 mm)

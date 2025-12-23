"""
Generate BackupForce app icon - Mountains + Database design
Creates PNG and ICO files for the application icon
"""
from PIL import Image, ImageDraw
import math

def create_icon(size):
    """Create the BackupForce icon at the specified size"""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Scale factor
    s = size / 256
    
    # Background circle with gradient effect (dark blue)
    center = size // 2
    radius = int(120 * s)
    
    # Draw gradient background circle
    for r in range(radius, 0, -1):
        # Gradient from dark blue at top to lighter blue at bottom
        progress = (radius - r) / radius
        color_top = (26, 31, 53)  # #1a1f35
        color_bottom = (74, 100, 145)  # #4a6491
        color = tuple(int(color_top[i] + (color_bottom[i] - color_top[i]) * progress) for i in range(3))
        draw.ellipse([center-r, center-r, center+r, center+r], fill=color + (255,))
    
    # Stars
    star_positions = [(60, 50, 2), (180, 40, 1.5), (200, 70, 2), (45, 80, 1), (150, 55, 1.5)]
    for x, y, r in star_positions:
        sx, sy, sr = int(x*s), int(y*s), max(1, int(r*s))
        draw.ellipse([sx-sr, sy-sr, sx+sr, sy+sr], fill=(255, 255, 255, 200))
    
    # Back mountain (darker)
    mountain1_points = [(30, 180), (90, 90), (130, 140), (170, 85), (230, 180)]
    mountain1_scaled = [(int(x*s), int(y*s)) for x, y in mountain1_points]
    draw.polygon(mountain1_scaled, fill=(61, 90, 128, 200))  # #3d5a80 with alpha
    
    # Snow caps on back mountain
    snow1 = [(90, 90), (80, 110), (100, 110)]
    snow2 = [(170, 85), (158, 108), (182, 108)]
    draw.polygon([(int(x*s), int(y*s)) for x, y in snow1], fill=(224, 232, 240, 180))
    draw.polygon([(int(x*s), int(y*s)) for x, y in snow2], fill=(224, 232, 240, 180))
    
    # Front mountain (lighter)
    mountain2_points = [(10, 200), (70, 120), (110, 160), (128, 130), (150, 155), (190, 115), (250, 200)]
    mountain2_scaled = [(int(x*s), int(y*s)) for x, y in mountain2_points]
    draw.polygon(mountain2_scaled, fill=(92, 122, 153, 255))  # #5c7a99
    
    # Snow caps on front mountain
    snow3 = [(70, 120), (58, 145), (82, 145)]
    snow4 = [(128, 130), (118, 150), (138, 150)]
    snow5 = [(190, 115), (178, 142), (202, 142)]
    for snow in [snow3, snow4, snow5]:
        draw.polygon([(int(x*s), int(y*s)) for x, y in snow], fill=(255, 255, 255, 230))
    
    # Database stack (3 cylinders) - cyan/teal color
    db_color = (0, 212, 170)  # #00d4aa
    db_highlight = (0, 245, 196)  # #00f5c4
    db_shadow = (0, 136, 102)  # #008866
    
    cx = int(128 * s)  # center x
    rx = int(45 * s)   # radius x (ellipse)
    ry = int(12 * s)   # radius y (ellipse)
    cyl_height = int(25 * s)
    
    # Draw 3 stacked cylinders from bottom to top
    for i, y_base in enumerate([220, 195, 170]):
        y = int(y_base * s)
        
        # Bottom ellipse (shadow)
        draw.ellipse([cx-rx, y-ry, cx+rx, y+ry], fill=db_shadow)
        
        # Cylinder body
        draw.rectangle([cx-rx, y-cyl_height, cx+rx, y], fill=db_color)
        
        # Top ellipse (highlight)
        draw.ellipse([cx-rx, y-cyl_height-ry, cx+rx, y-cyl_height+ry], fill=db_highlight)
        
        # Horizontal line on cylinder
        line_y = int((y_base - 12) * s)
        draw.line([cx-rx+int(5*s), line_y, cx+rx-int(5*s), line_y], fill=db_shadow, width=max(1, int(1.5*s)))
    
    # Sync arrows (white)
    arrow_color = (255, 255, 255, 230)
    
    # Right arrow (going up)
    # Draw arc approximation with lines
    points_right = []
    for angle in range(0, 91, 10):
        rad = math.radians(angle)
        px = int((180 + 15 * math.cos(rad)) * s)
        py = int((167.5 - 22.5 * math.sin(rad)) * s)
        points_right.append((px, py))
    if len(points_right) > 1:
        draw.line(points_right, fill=arrow_color, width=max(2, int(3*s)))
    
    # Arrow head (pointing up)
    arrow_head_r = [(178, 140), (185, 148), (175, 150)]
    draw.polygon([(int(x*s), int(y*s)) for x, y in arrow_head_r], fill=arrow_color)
    
    # Left arrow (going down)  
    points_left = []
    for angle in range(0, 91, 10):
        rad = math.radians(angle)
        px = int((76 - 15 * math.cos(rad)) * s)
        py = int((177.5 + 22.5 * math.sin(rad)) * s)
        points_left.append((px, py))
    if len(points_left) > 1:
        draw.line(points_left, fill=arrow_color, width=max(2, int(3*s)))
    
    # Arrow head (pointing down)
    arrow_head_l = [(78, 205), (71, 197), (81, 195)]
    draw.polygon([(int(x*s), int(y*s)) for x, y in arrow_head_l], fill=arrow_color)
    
    # Lightning bolt (gold/yellow for "Force")
    bolt_color = (255, 215, 0, 230)  # Gold
    bolt_points = [(128, 40), (120, 70), (132, 70), (125, 95), (145, 60), (133, 60), (140, 40)]
    bolt_scaled = [(int(x*s), int(y*s)) for x, y in bolt_points]
    draw.polygon(bolt_scaled, fill=bolt_color)
    
    # Border circle
    border_color = (0, 212, 170)  # #00d4aa (teal)
    border_width = max(2, int(4 * s))
    draw.ellipse([center-radius, center-radius, center+radius, center+radius], 
                 outline=border_color, width=border_width)
    
    return img

def main():
    # Create icons at multiple sizes for ICO
    sizes = [256, 128, 64, 48, 32, 16]
    images = []
    
    for size in sizes:
        print(f"Creating {size}x{size} icon...")
        img = create_icon(size)
        images.append(img)
    
    # Save PNG (256x256)
    png_path = r"c:\backupForce3\src\main\resources\images\backupforce-icon.png"
    images[0].save(png_path, "PNG")
    print(f"Saved PNG: {png_path}")
    
    # Save ICO with multiple sizes
    ico_path = r"c:\backupForce3\src\main\resources\images\backupforce-icon.ico"
    images[0].save(ico_path, format='ICO', sizes=[(s, s) for s in sizes])
    print(f"Saved ICO: {ico_path}")
    
    print("\n✅ Icon files created successfully!")

if __name__ == "__main__":
    main()

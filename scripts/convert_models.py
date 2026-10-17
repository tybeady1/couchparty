"""Convert Kenney OBJ palette models to compact position/normal/color triangle meshes.
Usage: python scripts/convert_models.py <extracted kit directory>
Original OBJ/MTL and palette are retained for editable source and license provenance.
"""
import sys, pathlib, struct, shutil, json
from PIL import Image

source=pathlib.Path(sys.argv[1]); root=pathlib.Path(__file__).resolve().parents[1]
objdir=source/'Models'/'OBJ format'; out=root/'app/src/main/assets/models'; out.mkdir(parents=True,exist_ok=True)
original=root/'third_party/kenney-minigolf'; original.mkdir(parents=True,exist_ok=True)
palette=Image.open(objdir/'Textures/colormap.png').convert('RGB')
names=['ball-blue','flag-red','flag-blue','flag-green','castle','structure-windmill','windmill','obstacle-block','obstacle-diamond','structure-gate','open']
manifest={}
for name in names:
    v=[]; uv=[]; normals=[]; faces=[]
    for line in (objdir/(name+'.obj')).read_text().splitlines():
        parts=line.split()
        if not parts: continue
        if parts[0]=='v':v.append(tuple(map(float,parts[1:4])))
        elif parts[0]=='vt':uv.append(tuple(map(float,parts[1:3])))
        elif parts[0]=='vn':normals.append(tuple(map(float,parts[1:4])))
        elif parts[0]=='f':
            inds=[tuple(int(i)-1 if i else -1 for i in p.split('/')) for p in parts[1:]]
            for i in range(1,len(inds)-1):faces.append([inds[0],inds[i],inds[i+1]])
    lo=[min(p[i] for p in v) for i in range(3)];hi=[max(p[i] for p in v) for i in range(3)]
    # Center footprint and ground the mesh; preserve original units/proportions.
    origin=[(lo[0]+hi[0])/2,lo[1],(lo[2]+hi[2])/2]
    floats=[]
    for face in faces:
        for vertex in face:
            pos=v[vertex[0]]; tex=uv[vertex[1]]; normal=normals[vertex[2]]
            color=palette.getpixel((min(palette.width-1,max(0,int(tex[0]*palette.width))),min(palette.height-1,max(0,int((1-tex[1])*palette.height)))))
            floats.extend([*(pos[i]-origin[i] for i in range(3)),*normal,*(c/255 for c in color)])
    (out/(name+'.mesh')).write_bytes(struct.pack('<I',len(floats)//9)+struct.pack('<'+'f'*len(floats),*floats))
    for ext in ['.obj','.mtl']:shutil.copyfile(objdir/(name+ext),original/(name+ext))
    manifest[name]={'vertices':len(floats)//9,'bounds':[hi[i]-lo[i] for i in range(3)]}
(original/'Textures').mkdir(exist_ok=True);shutil.copyfile(objdir/'Textures/colormap.png',original/'Textures/colormap.png')
shutil.copyfile(source/'License.txt',original/'License.txt');shutil.copyfile(source/'License.txt',out/'KENNEY-LICENSE.txt')
(out/'manifest.json').write_text(json.dumps(manifest,indent=2))
print(json.dumps(manifest,indent=2))

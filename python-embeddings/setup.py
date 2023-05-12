from setuptools import setup

setup(
    name = 'beam_embeddings',
    version = '0.1.0',    
    description = 'An simple embeddings extraction library for Apache Beam.',
    url = 'https://github.com/prodriguezdefino/googledoc-content-extraction',
    author = 'Pablo Rodriguez Defino',
    author_email = 'prodriguezdefino@gmail.com',
    license = 'Apache V2',
    package_dir = {'': 'src'},
    packages = ['beam.embeddings'],
    py_modules = ['beam_embeddings'],
    install_requires=[
        'apache_beam[gcp]==2.47.0', 
        'google-cloud-aiplatform==1.25', 
        'pandas'],

    classifiers=[
        'Development Status :: Prototype',
        'Intended Audience :: Science/Research',
        'Programming Language :: Python :: 3.8',
    ],
)
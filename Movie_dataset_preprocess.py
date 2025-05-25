import pandas as pd
import requests
import time
import numpy as np

# Load the CSV into a pandas DataFrame
df = pd.read_csv('src/main/resources/Movies/imdb_top_1000.csv')  

# Mapping dictionary based on the ontology
certification_mapping = {
    'A': 'ParentalGuidance(PG)',
    'UA': 'ParentalGuidance-13(PG-13)',
    'U': 'General(G)',
    'PG-13': 'ParentalGuidance-13(PG-13)',
    'R': 'Restricted(R)',
    'PG': 'ParentalGuidance(PG)',
    'G': 'General(G)',
    'Passed': 'General(G)',
    'TV-14': 'ParentalGuidance-13(PG-13)',
    '16': 'Restricted(R)',
    'TV-MA': 'NC-17(AdultsOnly)',
    'Unrated': 'NotRated(NR)',
    'GP': 'ParentalGuidance(PG)',
    'Approved': 'General(G)',
    'TV-PG': 'ParentalGuidance(PG)',
    'U/A': 'ParentalGuidance-13(PG-13)'
}

# Apply mapping without altering NaN values
df['Certificate'] = df['Certificate'].map(certification_mapping).combine_first(df['Certificate'])

# Split genres and limit to the first 3
genre_split = df['Genre'].str.split(',', n=3, expand=True)

# Create new columns for the split genres
df['Genre1'] = genre_split[0]
df['Genre2'] = genre_split[1]
df['Genre3'] = genre_split[2]

# Drop the original 'Genre' column
df = df.drop(columns=['Genre'])

# Save the modified DataFrame back to a new CSV file
df.to_csv('imdb_top_1000.csv', index=False)
